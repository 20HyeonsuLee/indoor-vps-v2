package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.area.FloorAreaResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 머지 실행을 호출자 스레드에서 분리. POST /merge는 검증만 동기로 처리하고
 * 즉시 응답하며, 실제 rtabmap-reprocess + post-merge reprocess + Stage 3
 * alignment + scan_metadata merge는 백그라운드에서 진행한다.
 *
 * Python bridge 호출은 분 단위로 걸릴 수 있어 동기로 두면 클라이언트가
 * timeout(60s) 안에 응답을 못 받음.
 */
@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class AsyncMergeExecutor {
    private static final Logger log = LoggerFactory.getLogger(AsyncMergeExecutor.class);

    private final ScanMergeRunner mergeRunner;
    private final ScanPersistence scanPersistence;
    private final FloorAreaResolver floorAreaResolver;

    public AsyncMergeExecutor(
            ScanMergeRunner mergeRunner,
            ScanPersistence scanPersistence,
            FloorAreaResolver floorAreaResolver
    ) {
        this.mergeRunner = mergeRunner;
        this.scanPersistence = scanPersistence;
        this.floorAreaResolver = floorAreaResolver;
    }

    /**
     * 백그라운드에서 머지 실행 + 완료 시 floor_scan을 active로 전환.
     * 클라는 chunks API를 polling해 머지본 등장을 확인한다.
     */
    @Async("mergeExecutor")
    @Transactional
    public void executeMerge(ScanMergeRunCommand cmd, UUID floorId, UUID areaId) {
        UUID mergedScanId = cmd.mergedScanId();
        log.info("[merge] start scan_id={} floor_id={} area_id={}", mergedScanId, floorId, areaId);
        try {
            MergeScanBridgeResponse merge = mergeRunner.run(cmd);
            persistMergedResult(cmd, floorId, areaId, merge);
            log.info("[merge] done scan_id={} sha={} bytes={}",
                    mergedScanId, merge.sha256(), merge.fileSize());
        } catch (RuntimeException e) {
            log.error("[merge] failed scan_id={}: {}", mergedScanId, e.getMessage(), e);
        }
    }

    private void persistMergedResult(
            ScanMergeRunCommand cmd,
            UUID floorId,
            UUID areaId,
            MergeScanBridgeResponse merge
    ) {
        FloorAreaEntity area = floorAreaResolver.resolve(floorId, java.util.Optional.of(areaId));
        ScanIngestEntity scan = scanPersistence.saveScan(new ScanIngestEntity(
                cmd.mergedScanId(),
                merge.sha256(),
                "scans/" + cmd.mergedScanId(),
                Map.of("merge", merge.diagnostics() == null ? Map.of() : merge.diagnostics()),
                area.getAreaId()
        ));
        scanPersistence.deactivateForArea(area.getAreaId());
        FloorScanEntity floorScan = new FloorScanEntity(
                area,
                scan,
                "merged_" + cmd.mergedScanId() + ".db",
                merge.fileSize(),
                scanPersistence.nextUploadOrder(floorId)
        );
        floorScan.changeStatus("MERGED");
        floorScan.changeActive(true);
        scanPersistence.saveScanEntity(floorScan);
    }
}
