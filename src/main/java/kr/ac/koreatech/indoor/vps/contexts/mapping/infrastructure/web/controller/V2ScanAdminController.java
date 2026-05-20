package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
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

    public V2ScanAdminController(IndoorProperties properties, RtabmapReprocessor reprocessor, PythonBridge bridge) {
        this.properties = properties;
        this.reprocessor = reprocessor;
        this.bridge = bridge;
    }

    @PostMapping("/{scanId}/rebuild-v2")
    public Map<String, Object> rebuildV2(@PathVariable UUID scanId) {
        Path scanDir = properties.getStorageRoot().resolve("scans").resolve(scanId.toString());
        Path v2Input = scanDir.resolve("v2").resolve("rtabmap.db");
        if (!Files.exists(v2Input)) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "V2_INPUT_MISSING",
                    "rtabmap-native db 파일이 없습니다: " + v2Input);
        }
        log.info("[v2-build] scan={} input={}", scanId, v2Input);

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
}
