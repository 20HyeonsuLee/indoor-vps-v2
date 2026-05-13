package kr.ac.koreatech.indoor.vps.application.persistence;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.application.persistence.ScanArchiveStorageService.StoredScanArchive;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildState;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.ScanIngestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanJpaService {
    private final BuildingJpaService buildingService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final BuildJobRepository buildJobRepository;
    private final ScanArchiveStorageService scanArchiveStorage;
    private final ObjectMapper objectMapper;

    public ScanJpaService(
            BuildingJpaService buildingService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            BuildJobRepository buildJobRepository,
            ScanArchiveStorageService scanArchiveStorage,
            ObjectMapper objectMapper
    ) {
        this.buildingService = buildingService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.buildJobRepository = buildJobRepository;
        this.scanArchiveStorage = scanArchiveStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScanChunkResponse uploadScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        FloorEntity floor = buildingService.requireFloor(floorId);
        UUID scanId = parseOrGenerate(scanIdText);
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

            FloorScanEntity floorScan = floorScanRepository.findByFloor_FloorIdAndScan_ScanId(floorId, scanId)
                    .orElseGet(() -> new FloorScanEntity(
                            floor,
                            persistedScan,
                            stored.fileName(),
                            stored.size(),
                            floorScanRepository.nextUploadOrder(floorId)
                    ));
            floorScan.setFileName(stored.fileName());
            floorScan.setFileSize(stored.size());
            floorScan.setStatus("UPLOADED");
            floorScan.setActive(true);
            floorScan = floorScanRepository.saveAndFlush(floorScan);
            return toScanChunkResponse(floorScan);
        } catch (RuntimeException e) {
            if (!existingScan) {
                scanArchiveStorage.deleteScan(scanId);
            }
            throw e;
        }
    }

    public List<ScanChunkResponse> listScanChunks(UUID floorId) {
        buildingService.requireFloor(floorId);
        return floorScanRepository.findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(floorId).stream()
                .map(this::toScanChunkResponse)
                .toList();
    }

    @Transactional
    public void deleteScanChunk(UUID floorId, UUID chunkId) {
        buildingService.requireFloor(floorId);
        FloorScanEntity floorScan = floorScanRepository.findByFloor_FloorIdAndFloorScanId(floorId, chunkId)
                .orElseThrow(() -> notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        UUID scanId = floorScan.getScan().getScanId();
        floorScanRepository.delete(floorScan);
        floorScanRepository.flush();
        if (!floorScanRepository.existsByScan_ScanId(scanId)) {
            scanIngestRepository.deleteById(scanId);
            scanArchiveStorage.deleteScan(scanId);
        }
    }

    @Transactional
    public MergedScanResponse mergeScans(UUID floorId, List<UUID> chunkIds) {
        buildingService.requireFloor(floorId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId);
        }
        FloorScanEntity target = floorScanRepository.findByFloor_FloorIdAndFloorScanId(floorId, chunkIds.get(0))
                .orElseThrow(() -> notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        target.setActive(true);
        target.setStatus("MERGED");
        floorScanRepository.saveAndFlush(target);
        return new MergedScanResponse(floorId, target.getScan().getScanId(), "MERGED");
    }

    public MergedScanResponse mergeStatus(UUID floorId) {
        buildingService.requireFloor(floorId);
        return buildingService.activeScan(floorId)
                .map(scan -> new MergedScanResponse(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResponse(floorId, null, "IDLE"));
    }

    @Transactional
    public ProcessingStatusResponse process(UUID floorId) {
        buildingService.requireFloor(floorId);
        FloorScanEntity active = buildingService.activeScan(floorId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.CONFLICT, "ACTIVE_SCAN_NOT_FOUND", "floor has no active scan"));
        ScanIngestEntity scan = active.getScan();
        BuildJobEntity job = buildJobRepository.saveAndFlush(new BuildJobEntity(scan));
        scan.setBuildState(BuildState.pending);
        scan.setBuildJobId(job.getBuildJobId());
        scanIngestRepository.saveAndFlush(scan);
        return new ProcessingStatusResponse(floorId, scan.getScanId(), job.getBuildJobId(), "QUEUED", 0.0, null);
    }

    public ProcessingStatusResponse processStatus(UUID floorId) {
        buildingService.requireFloor(floorId);
        Optional<FloorScanEntity> active = buildingService.activeScan(floorId);
        if (active.isEmpty()) {
            return new ProcessingStatusResponse(floorId, null, null, "IDLE", null, null);
        }
        UUID scanId = active.get().getScan().getScanId();
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(job -> new ProcessingStatusResponse(
                        floorId,
                        scanId,
                        job.getBuildJobId(),
                        publicBuildState(job.getState()),
                        job.getProgress(),
                        firstNonBlank(
                                job.getFailureReason() == null ? null : job.getFailureReason().name(),
                                job.getFailureDetail()
                        )
                ))
                .orElseGet(() -> new ProcessingStatusResponse(
                        floorId,
                        scanId,
                        null,
                        publicBuildState(active.get().getScan().getBuildState()),
                        null,
                        null
                ));
    }

    private ScanChunkResponse toScanChunkResponse(FloorScanEntity scan) {
        return new ScanChunkResponse(
                scan.getFloorScanId(),
                scan.getFloor().getFloorId(),
                scan.getScan().getScanId(),
                scan.getFileName(),
                scan.getFileSize(),
                scan.getStatus(),
                scan.isActive(),
                scan.getUploadOrder(),
                scan.getCreatedAt()
        );
    }

    private Map<String, Object> deviceInfoMap(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(deviceInfo, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of("raw", deviceInfo);
        }
    }

    private UUID parseOrGenerate(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCAN_ID", "invalid scan_id");
        }
    }

    private String publicBuildState(BuildState state) {
        if (state == null || state == BuildState.not_started) {
            return "IDLE";
        }
        if (state == BuildState.pending) {
            return "QUEUED";
        }
        return state.name().toUpperCase();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private ClientApiException notFound(String code, String message) {
        return new ClientApiException(HttpStatus.NOT_FOUND, code, message);
    }

}
