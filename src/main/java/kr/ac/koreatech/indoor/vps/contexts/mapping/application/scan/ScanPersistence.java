package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ScanIngestRepository + FloorScanRepository 공통 조합 연산을 모은 facade.
 * UseCase instance_vars 3 이하 유지를 위해 도입.
 */
@Component
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanPersistence {

    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final ScanArchiveStorage scanArchiveStorage;

    public ScanPersistence(
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            ScanArchiveStorage scanArchiveStorage
    ) {
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.scanArchiveStorage = scanArchiveStorage;
    }

    public boolean scanExists(UUID scanId) {
        return scanIngestRepository.existsById(scanId);
    }

    public Optional<ScanIngestEntity> findScan(UUID scanId) {
        return scanIngestRepository.findById(scanId);
    }

    @Transactional
    public ScanIngestEntity saveScan(ScanIngestEntity scan) {
        return scanIngestRepository.saveAndFlush(scan);
    }

    @Transactional
    public FloorScanEntity saveFloorScanActive(UUID floorId, FloorEntity floor, ScanIngestEntity scan,
            String fileName, long size, String status) {
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        FloorScanEntity floorScan = floorScanRepository
                .findByFloor_FloorIdAndScan_ScanId(floorId, scan.getScanId())
                .orElseGet(() -> new FloorScanEntity(
                        floor,
                        scan,
                        fileName,
                        size,
                        floorScanRepository.nextUploadOrder(floorId)
                ));
        floorScan.updateStoredFile(fileName, size, status);
        floorScan.changeActive(true);
        return floorScanRepository.saveAndFlush(floorScan);
    }

    @Transactional
    public void deactivateForFloor(UUID floorId) {
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
    }

    public List<FloorScanEntity> findMergeSources(UUID floorId, List<UUID> chunkIds) {
        return floorScanRepository.findMergeSources(floorId, chunkIds);
    }

    public Optional<FloorScanEntity> findFloorScan(UUID floorId, UUID scanId) {
        return floorScanRepository.findByFloor_FloorIdAndScan_ScanId(floorId, scanId);
    }

    public int nextUploadOrder(UUID floorId) {
        return floorScanRepository.nextUploadOrder(floorId);
    }

    @Transactional
    public FloorScanEntity saveScanEntity(FloorScanEntity floorScan) {
        return floorScanRepository.saveAndFlush(floorScan);
    }

    public List<FloorScanEntity> findByFloorOrdered(UUID floorId) {
        return floorScanRepository.findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(floorId);
    }

    public Optional<FloorScanEntity> findChunk(UUID floorId, UUID chunkId) {
        return floorScanRepository.findByFloor_FloorIdAndFloorScanId(floorId, chunkId);
    }

    @Transactional
    public void deleteChunkIfOrphan(FloorScanEntity floorScan) {
        UUID scanId = floorScan.getScan().getScanId();
        floorScanRepository.delete(floorScan);
        floorScanRepository.flush();
        if (!floorScanRepository.existsByScan_ScanId(scanId)) {
            scanIngestRepository.deleteById(scanId);
            scanArchiveStorage.deleteScan(scanId);
        }
    }
}
