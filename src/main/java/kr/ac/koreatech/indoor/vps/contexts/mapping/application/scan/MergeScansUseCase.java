package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class MergeScansUseCase {

    private final FloorQueryService floorService;
    private final ScanPersistence scanPersistence;
    private final ScanMergeRunner mergeRunner;

    public MergeScansUseCase(
            FloorQueryService floorService,
            ScanPersistence scanPersistence,
            ScanMergeRunner mergeRunner
    ) {
        this.floorService = floorService;
        this.scanPersistence = scanPersistence;
        this.mergeRunner = mergeRunner;
    }

    @Transactional
    public MergedScanResult merge(UUID floorId, List<UUID> chunkIds) {
        floorService.requireFloor(floorId);
        FloorAreaEntity area = floorService.defaultArea(floorId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "DEFAULT_AREA_NOT_FOUND", "floor has no default area"));
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId);
        }
        List<FloorScanEntity> sources = scanPersistence.findMergeSources(floorId, chunkIds);
        if (sources.isEmpty()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "scan chunk not found");
        }
        if (sources.size() != new java.util.HashSet<>(chunkIds).size()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "one or more scan chunks were not found");
        }
        if (sources.size() == 1) {
            return activateSingleMerge(floorId, sources.getFirst());
        }

        UUID mergedScanId = UUID.randomUUID();
        MergeScanBridgeResponse merge = mergeRunner.run(new ScanMergeRunCommand(floorId, mergedScanId, sources));

        ScanIngestEntity scan = scanPersistence.saveScan(new ScanIngestEntity(
                mergedScanId,
                merge.sha256(),
                "scans/" + mergedScanId,
                Map.of("merge", merge.diagnostics() == null ? Map.of() : merge.diagnostics()),
                area.getAreaId()
        ));
        scanPersistence.deactivateForFloor(floorId);
        FloorScanEntity floorScan = new FloorScanEntity(
                area,
                scan,
                "merged_" + mergedScanId + ".db",
                merge.fileSize(),
                scanPersistence.nextUploadOrder(floorId)
        );
        floorScan.changeStatus("MERGED");
        floorScan.changeActive(true);
        scanPersistence.saveScanEntity(floorScan);
        return new MergedScanResult(floorId, mergedScanId, "MERGED");
    }

    public MergedScanResult mergeStatus(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorService.activeScan(floorId)
                .map(scan -> new MergedScanResult(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResult(floorId, null, "IDLE"));
    }

    private MergedScanResult activateSingleMerge(UUID floorId, FloorScanEntity target) {
        scanPersistence.deactivateForFloor(floorId);
        target.changeActive(true);
        target.changeStatus("MERGED");
        scanPersistence.saveScanEntity(target);
        return new MergedScanResult(floorId, target.getScan().getScanId(), "MERGED");
    }
}
