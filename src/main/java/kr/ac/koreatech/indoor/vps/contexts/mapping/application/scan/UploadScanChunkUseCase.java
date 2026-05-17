package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanArchiveStorage.StoredScanArchive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UploadScanChunkUseCase {

    private final FloorQueryService floorService;
    private final ScanArchiveStorage scanArchiveStorage;
    private final ScanPersistence scanPersistence;

    public UploadScanChunkUseCase(
            FloorQueryService floorService,
            ScanArchiveStorage scanArchiveStorage,
            ScanPersistence scanPersistence
    ) {
        this.floorService = floorService;
        this.scanArchiveStorage = scanArchiveStorage;
        this.scanPersistence = scanPersistence;
    }

    @Transactional
    public ScanChunkResult execute(UploadScanChunkCommand command) {
        FloorEntity floor = floorService.requireFloor(command.floorId());
        UUID scanId = scanArchiveStorage.resolveScanId(
                command.fileContent(), command.originalFilename(),
                parseOptional(command.scanIdText()).orElse(null)
        );
        boolean existingScan = scanPersistence.scanExists(scanId);
        if (existingScan && !command.force()) {
            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
        }
        StoredScanArchive stored = scanArchiveStorage.store(scanId, command.fileContent(), command.originalFilename(), command.force());

        try {
            ScanIngestEntity scan = scanPersistence.findScan(scanId)
                    .map(existing -> {
                        existing.replacePayload(stored.sha256(), stored.storagePath(), deviceInfoMap(command.deviceInfo()));
                        return existing;
                    })
                    .orElseGet(() -> new ScanIngestEntity(
                            scanId,
                            stored.sha256(),
                            stored.storagePath(),
                            deviceInfoMap(command.deviceInfo())
                    ));
            ScanIngestEntity persistedScan = scanPersistence.saveScan(scan);
            FloorScanEntity floorScan = scanPersistence.saveFloorScanActive(
                    command.floorId(), floor, persistedScan,
                    stored.fileName(), stored.size(), "UPLOADED"
            );
            return toScanChunkResult(floorScan);
        } catch (RuntimeException e) {
            if (!existingScan) {
                scanArchiveStorage.deleteScan(scanId);
            }
            throw e;
        }
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
            return new ObjectMapper().readValue(deviceInfo, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of("raw", deviceInfo);
        }
    }
}
