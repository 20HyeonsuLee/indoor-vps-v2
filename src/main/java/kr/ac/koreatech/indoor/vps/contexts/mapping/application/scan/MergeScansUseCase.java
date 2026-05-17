package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class MergeScansUseCase {

    private final FloorQueryService floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final PythonBridge bridge;
    private final IndoorProperties properties;

    public MergeScansUseCase(
            FloorQueryService floorService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            PythonBridge bridge,
            IndoorProperties properties
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.bridge = bridge;
        this.properties = properties;
    }

    @Transactional
    public MergedScanResult merge(UUID floorId, List<UUID> chunkIds) {
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
        MergeScanBridgeResponse merge = bridge.mergeScan(new MergeScanBridgeRequest(
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
        return new MergedScanResult(floorId, mergedScanId, "MERGED");
    }

    public MergedScanResult mergeStatus(UUID floorId) {
        floorService.requireFloor(floorId);
        return floorService.activeScan(floorId)
                .map(scan -> new MergedScanResult(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResult(floorId, null, "IDLE"));
    }

    private MergedScanResult activateSingleMerge(UUID floorId, FloorScanEntity target) {
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        target.changeActive(true);
        target.changeStatus("MERGED");
        floorScanRepository.saveAndFlush(target);
        return new MergedScanResult(floorId, target.getScan().getScanId(), "MERGED");
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
