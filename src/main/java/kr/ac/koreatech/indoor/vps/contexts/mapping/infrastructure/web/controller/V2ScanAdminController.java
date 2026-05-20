package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.DepthRangeMasker;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.RtabmapReprocessor.RtabmapReprocessResult;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * rtabmap-native(.db) 변종을 직접 빌드하는 admin endpoint. 사용자가
 * {@code <storage_root>/scans/<scanId>/v2/rtabmap.db} 파일을 직접 넣어두면
 * 이 endpoint가 reprocess + SuperPoint index build를 동기 실행.
 *
 * <p>localize는 {@link kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationFacade}
 * 가 자동으로 shadow path로 활용 (LocalizationMapProvider.activeFloorMapsV2가 이 산출물을 가리킴).
 */
@RestController
@RequestMapping("/api/admin/scans")
@Tag(name = "Admin")
public class V2ScanAdminController {
    private static final Logger log = LoggerFactory.getLogger(V2ScanAdminController.class);

    private final IndoorProperties properties;
    private final RtabmapReprocessor reprocessor;
    private final PythonBridge bridge;
    private final DataSource dataSource;

    public V2ScanAdminController(
            IndoorProperties properties,
            RtabmapReprocessor reprocessor,
            PythonBridge bridge,
            DataSource dataSource
    ) {
        this.properties = properties;
        this.reprocessor = reprocessor;
        this.bridge = bridge;
        this.dataSource = dataSource;
    }

    @PostMapping("/{scanId}/rebuild-v2")
    public Map<String, Object> rebuildV2(
            @PathVariable UUID scanId,
            @RequestParam(value = "areaId", required = false) UUID areaId
    ) {
        Path scanDir = properties.getStorageRoot().resolve("scans").resolve(scanId.toString());
        Path v2Input = scanDir.resolve("v2").resolve("rtabmap.db");
        if (!Files.exists(v2Input)) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "V2_INPUT_MISSING",
                    "rtabmap-native db 파일이 없습니다: " + v2Input);
        }
        log.info("[v2-build] scan={} input={} areaId={}", scanId, v2Input, areaId);

        // areaId가 주어지면 scan_ingest/floor_scan/build_job row를 자동 upsert. admin이
        // 디스크 우회로로 v2/rtabmap.db만 넣어두던 케이스에서, 후속 manual edit
        // (코너/노드)이 ManualEditScopeResolver에서 build_job_id를 요구하므로 누락 시
        // SCAN_NOT_BUILT 발생하던 문제를 영구 해결.
        if (areaId != null) {
            int rows = registerScanIfMissing(scanId, areaId);
            log.info("[v2-build] auto-registered scan rows affected={}", rows);
        }

        RtabmapReprocessResult reprocess = reprocessor.reprocess(scanId, v2Input);
        Path reprocessedDb = reprocess.effectiveDbPath();
        if (!Files.exists(reprocessedDb)) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "V2_REPROCESS_FAILED",
                    "reprocess 결과 db 없음: " + reprocessedDb + " (reason=" + reprocess.reason() + ")");
        }

        BuildSuperpointIndexResponse indexResult = bridge.buildSuperpointIndex(
                new BuildSuperpointIndexRequest(scanId.toString(), reprocessedDb.toAbsolutePath().toString())
        );

        // cloud.ply export — v2/cloud.ply. PointcloudController가 ?variant=v2 로 노출.
        Path cloudPath = reprocessedDb.getParent().resolve("cloud.ply");
        ExportPointcloudResponse cloud = null;
        try {
            cloud = bridge.exportPointcloud(new ExportPointcloudRequest(
                    scanId.toString(),
                    reprocessedDb.toAbsolutePath().toString(),
                    cloudPath.toAbsolutePath().toString()
            ));
            log.info("[v2-cloud] {} points={} bytes={} elapsed={}ms",
                    cloudPath, cloud.pointCount(), cloud.fileSize(), cloud.elapsedMs());
        } catch (RuntimeException e) {
            log.warn("[v2-cloud] export failed (continuing): {}", e.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scanId", scanId.toString());
        body.put("v2DbPath", reprocessedDb.toString());
        body.put("reprocessReason", reprocess.reason());
        body.put("indexFrameCount", indexResult.frameCount());
        body.put("indexTotalKeypoints", indexResult.totalKeypoints());
        body.put("indexBytes", indexResult.bytes());
        body.put("indexElapsedMs", indexResult.elapsedMs());
        if (cloud != null) {
            body.put("cloudPath", cloud.plyPath());
            body.put("cloudPointCount", cloud.pointCount());
            body.put("cloudBytes", cloud.fileSize());
            body.put("cloudElapsedMs", cloud.elapsedMs());
        }
        return body;
    }

    /**
     * scan_ingest + floor_scan + build_job row를 보강. ON CONFLICT DO NOTHING이라
     * 이미 정상 등록된 scan에 호출해도 부작용 0.
     */
    private int registerScanIfMissing(UUID scanId, UUID areaId) {
        int rows = 0;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            UUID floorId = findFloorId(c, areaId).orElseThrow(() -> new ClientApiException(
                    HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId));

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO scan_ingest (scan_id, payload_sha256, storage_path, build_state, area_id) "
                  + "VALUES (?, ?, ?, 'succeeded', ?) ON CONFLICT (scan_id) DO NOTHING")) {
                ps.setObject(1, scanId);
                ps.setString(2, "v2_admin_" + scanId);
                ps.setString(3, "scans/" + scanId);
                ps.setObject(4, areaId);
                rows += ps.executeUpdate();
            }

            // build_job — scan_ingest의 build_job_id가 null이면 생성 후 연결.
            UUID existingJob = readBuildJobId(c, scanId).orElse(null);
            UUID buildJobId = existingJob != null ? existingJob : UUID.randomUUID();
            if (existingJob == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO build_job (build_job_id, scan_id, state, area_id, started_at, finished_at) "
                      + "VALUES (?, ?, 'succeeded', ?, now(), now())")) {
                    ps.setObject(1, buildJobId);
                    ps.setObject(2, scanId);
                    ps.setObject(3, areaId);
                    rows += ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE scan_ingest SET build_job_id = ? WHERE scan_id = ?")) {
                    ps.setObject(1, buildJobId);
                    ps.setObject(2, scanId);
                    rows += ps.executeUpdate();
                }
            }

            // floor_scan — 같은 area의 다른 active row는 false로 (uq_floor_scan_one_active 충돌 회피).
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE floor_scan SET active = false WHERE area_id = ? AND active = true AND scan_id <> ?")) {
                ps.setObject(1, areaId);
                ps.setObject(2, scanId);
                rows += ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO floor_scan (floor_scan_id, floor_id, scan_id, status, active, area_id) "
                  + "VALUES (?, ?, ?, 'READY', true, ?) ON CONFLICT (floor_id, scan_id) DO UPDATE SET active = true")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, floorId);
                ps.setObject(3, scanId);
                ps.setObject(4, areaId);
                rows += ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            log.warn("[v2-build] auto-register failed: {}", e.getMessage());
        }
        return rows;
    }

    private Optional<UUID> findFloorId(Connection c, UUID areaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT floor_id FROM floor_area WHERE area_id = ?")) {
            ps.setObject(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    private Optional<UUID> readBuildJobId(Connection c, UUID scanId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT build_job_id FROM scan_ingest WHERE scan_id = ?")) {
            ps.setObject(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String s = rs.getString(1);
                return s == null ? Optional.empty() : Optional.of(UUID.fromString(s));
            }
        }
    }

    private static final int MAX_DEPTH_RANGE_MM = 5000;

    private int maskV2DepthInPlace(Path db) {
        int count = 0;
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);
            try (PreparedStatement select = c.prepareStatement(
                    "SELECT id, depth, depth_confidence FROM Data WHERE depth IS NOT NULL");
                 PreparedStatement update = c.prepareStatement(
                    "UPDATE Data SET depth = ?, depth_confidence = ? WHERE id = ?");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    byte[] depth = rs.getBytes(2);
                    byte[] conf = rs.getBytes(3);
                    DepthRangeMasker.Result r = DepthRangeMasker.mask(depth, conf, MAX_DEPTH_RANGE_MM);
                    update.setBytes(1, r.depth());
                    update.setBytes(2, r.confidence());
                    update.setInt(3, id);
                    update.executeUpdate();
                    count++;
                }
            }
            c.commit();
        } catch (SQLException e) {
            log.warn("[v2-build] depth in-place masking failed: {}", e.getMessage());
        }
        return count;
    }
}
