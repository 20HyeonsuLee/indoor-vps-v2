package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.PythonBridgeClient;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorApplicationService;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.ScanArchiveStorageService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.ScanArchiveStorageService.StoredScanArchive;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.StreamingScanStorageService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.StreamingScanStorageService.FinalizedStreamingScan;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.StreamingScanStorageService.StartedStreamingScan;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage.StreamingScanStorageService.StreamingFrameStats;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanApplicationService {
    private final FloorApplicationService floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final BuildJobRepository buildJobRepository;
    private final ScanArchiveStorageService scanArchiveStorage;
    private final StreamingScanStorageService streamingScanStorage;
    private final PythonBridgeClient bridgeClient;
    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    public ScanApplicationService(
            FloorApplicationService floorService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            BuildJobRepository buildJobRepository,
            ScanArchiveStorageService scanArchiveStorage,
            StreamingScanStorageService streamingScanStorage,
            PythonBridgeClient bridgeClient,
            IndoorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.buildJobRepository = buildJobRepository;
        this.scanArchiveStorage = scanArchiveStorage;
        this.streamingScanStorage = streamingScanStorage;
        this.bridgeClient = bridgeClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ScanStartResponse startStreamingScan(UUID floorId, ScanStartRequest request) {
        floorService.requireFloor(floorId);
        UUID scanId = parseOptional(request == null ? null : request.scanId());
        if (scanId == null) {
            scanId = UUID.randomUUID();
        }
        if (scanIngestRepository.existsById(scanId)) {
            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
        }
        StartedStreamingScan started = streamingScanStorage.start(
                floorId,
                scanId,
                deviceInfoMap(request == null ? null : request.deviceInfo())
        );
        return new ScanStartResponse(started.scanId(), started.floorId(), started.storagePath(), started.state());
    }

    public ScanFramesResponse pushStreamingFrames(UUID scanId, ScanFramesRequest request) {
        StreamingFrameStats stats = streamingScanStorage.append(scanId, request);
        return new ScanFramesResponse(
                stats.scanId(),
                stats.framesApplied(),
                stats.framesSkipped(),
                stats.linksApplied(),
                stats.linksSkipped(),
                stats.lastNodeId(),
                stats.nodeCount()
        );
    }

    @Transactional
    public ScanFinalizeResponse finalizeStreamingScan(
            UUID scanId,
            MultipartFile manifest,
            MultipartFile metadata
    ) {
        FinalizedStreamingScan finalized = streamingScanStorage.finalizeScan(scanId, manifest, metadata);
        FloorEntity floor = floorService.requireFloor(finalized.floorId());
        ScanIngestEntity scan = scanIngestRepository.findById(scanId)
                .map(existing -> {
                    existing.replacePayload(finalized.payloadSha256(), finalized.storagePath(), finalized.deviceInfo());
                    return existing;
                })
                .orElseGet(() -> new ScanIngestEntity(
                        scanId,
                        finalized.payloadSha256(),
                        finalized.storagePath(),
                        finalized.deviceInfo()
                ));
        ScanIngestEntity persistedScan = scanIngestRepository.saveAndFlush(scan);

        floorScanRepository.deactivateForFloor(finalized.floorId());
        floorScanRepository.flush();
        String fileName = streamingScanFileName(scanId);
        FloorScanEntity floorScan = floorScanRepository.findByFloor_FloorIdAndScan_ScanId(finalized.floorId(), scanId)
                .orElseGet(() -> new FloorScanEntity(
                        floor,
                        persistedScan,
                        fileName,
                        finalized.fileSize(),
                        floorScanRepository.nextUploadOrder(finalized.floorId())
                ));
        floorScan.updateStoredFile(fileName, finalized.fileSize(), "READY");
        floorScan.changeActive(true);
        floorScanRepository.saveAndFlush(floorScan);
        return new ScanFinalizeResponse(
                scanId,
                finalized.floorId(),
                "READY",
                finalized.nodeCount(),
                finalized.keyframeCount(),
                finalized.poiMarkCount(),
                finalized.payloadSha256()
        );
    }

    @Transactional
    public ScanChunkResponse uploadScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        FloorEntity floor = floorService.requireFloor(floorId);
        UUID scanId = scanArchiveStorage.resolveScanId(upload, parseOptional(scanIdText));
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

    public List<ScanChunkResponse> listScanChunks(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorScanRepository.findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(floorId).stream()
                .map(this::toScanChunkResponse)
                .toList();
    }

    @Transactional
    public void deleteScanChunk(UUID floorId, UUID chunkId) {
        floorService.requireFloor(floorId);
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
        FloorEntity floor = floorService.requireFloor(floorId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId);
        }
        List<FloorScanEntity> sources = floorScanRepository.findMergeSources(floorId, chunkIds);
        if (sources.isEmpty()) {
            throw notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found");
        }
        if (sources.size() != new java.util.HashSet<>(chunkIds).size()) {
            throw notFound("SCAN_CHUNK_NOT_FOUND", "one or more scan chunks were not found");
        }
        if (sources.size() == 1) {
            return activateSingleMerge(floorId, sources.getFirst());
        }

        UUID mergedScanId = UUID.randomUUID();
        Path outputDir = properties.getStorageRoot().resolve("scans").resolve(mergedScanId.toString());
        MergeScanBridgeResponse merge = bridgeClient.mergeScan(new MergeScanBridgeRequest(
                floorId,
                mergedScanId,
                sources.stream()
                        .map(source -> rtabmapDbPath(source.getScan().getStoragePath()).toString())
                        .toList(),
                outputDir.toString()
        ));

        ScanIngestEntity scan = scanIngestRepository.saveAndFlush(new ScanIngestEntity(
                mergedScanId,
                merge.sha256(),
                "scans/" + mergedScanId,
                Map.of("merge", merge.diagnostics() == null ? Map.of() : merge.diagnostics())
        ));
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        FloorScanEntity floorScan = new FloorScanEntity(
                floor,
                scan,
                "merged_" + mergedScanId + ".db",
                merge.fileSize(),
                floorScanRepository.nextUploadOrder(floorId)
        );
        floorScan.changeStatus("MERGED");
        floorScan.changeActive(true);
        floorScanRepository.saveAndFlush(floorScan);
        return new MergedScanResponse(floorId, mergedScanId, "MERGED");
    }

    private MergedScanResponse activateSingleMerge(UUID floorId, FloorScanEntity target) {
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        target.changeActive(true);
        target.changeStatus("MERGED");
        floorScanRepository.saveAndFlush(target);
        return new MergedScanResponse(floorId, target.getScan().getScanId(), "MERGED");
    }

    public MergedScanResponse mergeStatus(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorService.activeScan(floorId)
                .map(scan -> new MergedScanResponse(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResponse(floorId, null, "IDLE"));
    }

    @Transactional
    public ProcessingStatusResponse process(UUID floorId) {
        floorService.requireFloor(floorId);
        FloorScanEntity active = floorService.activeScan(floorId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.CONFLICT, "ACTIVE_SCAN_NOT_FOUND", "floor has no active scan"));
        ScanIngestEntity scan = active.getScan();
        BuildJobEntity job = buildJobRepository.saveAndFlush(new BuildJobEntity(scan));
        scan.changeBuildState(BuildState.pending);
        scan.attachBuildJob(job.getBuildJobId());
        scanIngestRepository.saveAndFlush(scan);
        return new ProcessingStatusResponse(floorId, scan.getScanId(), job.getBuildJobId(), "QUEUED", 0.0, null);
    }

    public ProcessingStatusResponse processStatus(UUID floorId) {
        floorService.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorService.activeScan(floorId);
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
                publicScanFileName(scan),
                scan.getFileSize(),
                scan.getStatus(),
                scan.isActive(),
                scan.getUploadOrder(),
                scan.getCreatedAt()
        );
    }

    private String publicScanFileName(FloorScanEntity scan) {
        String fileName = scan.getFileName();
        if (fileName == null || fileName.isBlank() || "rtabmap.db".equals(fileName)) {
            return streamingScanFileName(scan.getScan().getScanId());
        }
        return fileName;
    }

    private String streamingScanFileName(UUID scanId) {
        return scanId + ".db";
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

    private UUID parseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
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

    private Path rtabmapDbPath(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null && "rtabmap.db".equals(fileName.toString())) {
            return path;
        }
        if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            return path.getParent().resolve("rtabmap.db");
        }
        return path.resolve("rtabmap.db");
    }

}
