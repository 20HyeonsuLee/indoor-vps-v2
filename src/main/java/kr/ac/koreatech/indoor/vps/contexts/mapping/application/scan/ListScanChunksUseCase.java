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
public class ListScanChunksUseCase {

    private final FloorQueryService floorService;
    private final FloorAreaResolver floorAreaResolver;
    private final ScanPersistence scanPersistence;

    public ListScanChunksUseCase(
            FloorQueryService floorService,
            FloorAreaResolver floorAreaResolver,
            ScanPersistence scanPersistence
    ) {
        this.floorService = floorService;
        this.floorAreaResolver = floorAreaResolver;
        this.scanPersistence = scanPersistence;
    }

    public List<ScanChunkResult> listChunks(UUID floorId, Optional<UUID> areaId) {
        floorService.requireFloor(floorId);
        if (areaId.isPresent()) {
            FloorAreaEntity area = floorAreaResolver.resolve(floorId, areaId);
            return scanPersistence.findByAreaOrdered(area.getAreaId()).stream()
                    .map(this::toScanChunkResult)
                    .toList();
        }
        return scanPersistence.findByFloorOrdered(floorId).stream()
                .map(this::toScanChunkResult)
                .toList();
    }

    public List<ScanChunkResult> listChunks(UUID floorId) {
        return listChunks(floorId, Optional.empty());
    }

    @Transactional
    public void deleteChunk(UUID floorId, UUID chunkId) {
        floorService.requireFloor(floorId);
        FloorScanEntity floorScan = scanPersistence.findChunk(floorId, chunkId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        scanPersistence.deleteChunkIfOrphan(floorScan);
    }

    private ScanChunkResult toScanChunkResult(FloorScanEntity scan) {
        return new ScanChunkResult(
                scan.getFloorScanId(),
                scan.getFloor().getFloorId(),
                scan.getScan().getScanId(),
                ScanNaming.publicScanFileName(scan),
                scan.getFileSize(),
                scan.getStatus(),
                scan.isActive(),
                scan.getUploadOrder(),
                scan.getCreatedAt()
        );
    }
}
