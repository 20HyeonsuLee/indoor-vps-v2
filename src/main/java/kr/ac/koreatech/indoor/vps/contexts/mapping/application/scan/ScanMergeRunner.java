package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PythonBridge + IndoorProperties 조합 merge 실행 로직을 캡슐화.
 * MergeScansUseCase instance_vars 3 이하 유지를 위해 도입.
 */
@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ScanMergeRunner {

    private final PythonBridge bridge;
    private final IndoorProperties properties;

    public ScanMergeRunner(PythonBridge bridge, IndoorProperties properties) {
        this.bridge = bridge;
        this.properties = properties;
    }

    public MergeScanBridgeResponse run(UUID floorId, UUID mergedScanId, List<FloorScanEntity> sources) {
        Path outputDir = properties.getStorageRoot().resolve("scans").resolve(mergedScanId.toString());
        return bridge.mergeScan(new MergeScanBridgeRequest(
                floorId,
                mergedScanId,
                sources.stream()
                        .map(source -> rtabmapDbPath(source.getScan().getStoragePath()).toString())
                        .toList(),
                outputDir.toString()
        ));
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
