package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.ScanChunkResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ListScanChunksUseCase {

    private final FloorUseCase floorService;
    private final FloorScanRepository floorScanRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final ScanArchiveStorage scanArchiveStorage;

    public ListScanChunksUseCase(
            FloorUseCase floorService,
            FloorScanRepository floorScanRepository,
            ScanIngestRepository scanIngestRepository,
            ScanArchiveStorage scanArchiveStorage
    ) {
        this.floorService = floorService;
        this.floorScanRepository = floorScanRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.scanArchiveStorage = scanArchiveStorage;
    }

    public List<ScanChunkResponse> listChunks(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorScanRepository.findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(floorId).stream()
                .map(this::toScanChunkResponse)
                .toList();
    }

    @Transactional
    public void deleteChunk(UUID floorId, UUID chunkId) {
        floorService.requireFloor(floorId);
        FloorScanEntity floorScan = floorScanRepository
                .findByFloor_FloorIdAndFloorScanId(floorId, chunkId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        UUID scanId = floorScan.getScan().getScanId();
        floorScanRepository.delete(floorScan);
        floorScanRepository.flush();
        if (!floorScanRepository.existsByScan_ScanId(scanId)) {
            scanIngestRepository.deleteById(scanId);
            scanArchiveStorage.deleteScan(scanId);
        }
    }

    private ScanChunkResponse toScanChunkResponse(FloorScanEntity scan) {
        return new ScanChunkResponse(
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
