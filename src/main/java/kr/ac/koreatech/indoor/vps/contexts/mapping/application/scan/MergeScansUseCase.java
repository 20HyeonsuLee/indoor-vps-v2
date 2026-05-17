package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.PythonBridgeClient;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.MergedScanResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class MergeScansUseCase {

    private final FloorUseCase floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final PythonBridgeClient bridgeClient;
    private final IndoorProperties properties;

    public MergeScansUseCase(
            FloorUseCase floorService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            PythonBridgeClient bridgeClient,
            IndoorProperties properties
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.bridgeClient = bridgeClient;
        this.properties = properties;
    }

    @Transactional
    public MergedScanResponse merge(UUID floorId, List<UUID> chunkIds) {
        FloorEntity floor = floorService.requireFloor(floorId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId);
        }
        List<FloorScanEntity> sources = floorScanRepository.findMergeSources(floorId, chunkIds);
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

    public MergedScanResponse mergeStatus(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorService.activeScan(floorId)
                .map(scan -> new MergedScanResponse(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResponse(floorId, null, "IDLE"));
    }

    private MergedScanResponse activateSingleMerge(UUID floorId, FloorScanEntity target) {
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        target.changeActive(true);
        target.changeStatus("MERGED");
        floorScanRepository.saveAndFlush(target);
        return new MergedScanResponse(floorId, target.getScan().getScanId(), "MERGED");
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
