package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.area.FloorAreaResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class MergeScansUseCase {

    private final FloorQueryService floorService;
    private final FloorAreaResolver floorAreaResolver;
    private final ScanPersistence scanPersistence;
    private final AsyncMergeExecutor asyncMergeExecutor;

    public MergeScansUseCase(
            FloorQueryService floorService,
            FloorAreaResolver floorAreaResolver,
            ScanPersistence scanPersistence,
            AsyncMergeExecutor asyncMergeExecutor
    ) {
        this.floorService = floorService;
        this.floorAreaResolver = floorAreaResolver;
        this.scanPersistence = scanPersistence;
        this.asyncMergeExecutor = asyncMergeExecutor;
    }

    @Transactional
    public MergedScanResult merge(UUID floorId, List<UUID> chunkIds, Optional<UUID> areaId) {
        floorService.requireFloor(floorId);
        FloorAreaEntity area = floorAreaResolver.resolve(floorId, areaId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId, areaId);
        }
        List<FloorScanEntity> sources = scanPersistence.findMergeSources(floorId, chunkIds);
        if (sources.isEmpty()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "scan chunk not found");
        }
        if (sources.size() != new java.util.HashSet<>(chunkIds).size()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "one or more scan chunks were not found");
        }
        for (FloorScanEntity source : sources) {
            if (!source.getArea().getAreaId().equals(area.getAreaId())) {
                throw new ClientApiException(HttpStatus.BAD_REQUEST, "SCAN_CHUNK_AREA_MISMATCH",
                        "chunk " + source.getFloorScanId() + " does not belong to area " + area.getAreaId());
            }
        }
        if (sources.size() == 1) {
            return activateSingleMerge(floorId, area, sources.getFirst());
        }

        // 다중 청크 머지는 Python bridge 호출이 길어(rtabmap-reprocess + 후처리
        // 합쳐 분 단위) 동기로 두면 클라이언트가 60초 안에 응답을 못 받음.
        // 백그라운드 실행으로 분리하고 즉시 MERGING으로 응답한다. 클라는
        // chunks API를 polling해 머지본 등장을 확인한다.
        UUID mergedScanId = UUID.randomUUID();
        asyncMergeExecutor.executeMerge(
                new ScanMergeRunCommand(floorId, mergedScanId, sources),
                floorId,
                area.getAreaId()
        );
        return new MergedScanResult(floorId, mergedScanId, "MERGING");
    }

    public MergedScanResult mergeStatus(UUID floorId, Optional<UUID> areaId) {
        floorService.requireFloor(floorId);
        return floorService.activeScanForArea(floorId, areaId)
                .map(scan -> new MergedScanResult(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResult(floorId, null, "IDLE"));
    }

    public MergedScanResult mergeStatus(UUID floorId) {
        return mergeStatus(floorId, Optional.empty());
    }

    private MergedScanResult activateSingleMerge(UUID floorId, FloorAreaEntity area, FloorScanEntity target) {
        // 단일 청크 머지 — area 당 active 1개 강제. 다른 active 청크는 deactivate.
        scanPersistence.deactivateForArea(area.getAreaId());
        FloorScanEntity refreshed = scanPersistence.findChunk(floorId, target.getFloorScanId())
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        refreshed.changeActive(true);
        refreshed.changeStatus("MERGED");
        scanPersistence.saveScanEntity(refreshed);
        return new MergedScanResult(floorId, refreshed.getScan().getScanId(), "MERGED");
    }
}
