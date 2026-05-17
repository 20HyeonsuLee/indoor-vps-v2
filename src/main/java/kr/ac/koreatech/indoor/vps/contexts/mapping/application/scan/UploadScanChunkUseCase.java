package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage.StoredScanArchive;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.ScanChunkResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UploadScanChunkUseCase {

    private final FloorUseCase floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final ScanArchiveStorage scanArchiveStorage;
    private final ObjectMapper objectMapper;

    public UploadScanChunkUseCase(
            FloorUseCase floorService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            ScanArchiveStorage scanArchiveStorage,
            ObjectMapper objectMapper
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.scanArchiveStorage = scanArchiveStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScanChunkResponse execute(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        FloorEntity floor = floorService.requireFloor(floorId);
        UUID scanId = scanArchiveStorage.resolveScanId(upload, parseOptional(scanIdText).orElse(null));
        boolean existingScan = scanIngestRepository.existsById(scanId);
        if (existingScan && !force) {
            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
        }
        StoredScanArchive stored = scanArchiveStorage.store(scanId, upload, force);

        try {
            ScanIngestEntity scan = scanIngestRepository.findById(scanId)
                    .map(existing -> {
                        existing.replacePayload(stored.sha256(), stored.storagePath(), deviceInfoMap(deviceInfo));
                        return existing;
                    })
                    .orElseGet(() -> new ScanIngestEntity(
                            scanId,
                            stored.sha256(),
                            stored.storagePath(),
                            deviceInfoMap(deviceInfo)
                    ));
            ScanIngestEntity persistedScan = scanIngestRepository.saveAndFlush(scan);

            floorScanRepository.deactivateForFloor(floorId);
            floorScanRepository.flush();

            FloorScanEntity floorScan = floorScanRepository
                    .findByFloor_FloorIdAndScan_ScanId(floorId, scanId)
                    .orElseGet(() -> new FloorScanEntity(
                            floor,
                            persistedScan,
                            stored.fileName(),
                            stored.size(),
                            floorScanRepository.nextUploadOrder(floorId)
                    ));
            floorScan.updateStoredFile(stored.fileName(), stored.size(), "UPLOADED");
            floorScan.changeActive(true);
            floorScan = floorScanRepository.saveAndFlush(floorScan);
            return toScanChunkResponse(floorScan);
        } catch (RuntimeException e) {
            if (!existingScan) {
                scanArchiveStorage.deleteScan(scanId);
            }
            throw e;
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

    private Optional<UUID> parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCAN_ID", "invalid scan_id");
        }
    }

    private Map<String, Object> deviceInfoMap(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(deviceInfo, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of("raw", deviceInfo);
        }
    }
}
