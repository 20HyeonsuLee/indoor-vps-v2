package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.nio.file.Path;
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

    public MergeScanBridgeResponse run(ScanMergeRunCommand cmd) {
        Path outputDir = properties.getStorageRoot().resolve("scans").resolve(cmd.mergedScanId().toString());
        return bridge.mergeScan(new MergeScanBridgeRequest(
                cmd.floorId(),
                cmd.mergedScanId(),
                cmd.sources().stream()
                        .map(source -> sourceDbForMerge(source.getScan().getStoragePath()).toString())
                        .toList(),
                outputDir.toString()
        ));
    }

    /**
     * Source가 reprocess된 결과(rtabmap_reprocessed.db)가 있으면 그것을 머지 입력으로 사용.
     * 단일 스캔 reprocess는 풍부한 loop closure를 추가 검출(예: 409 노드 스캔에서 167건)
     * 하므로 머지 시작점이 훨씬 깨끗함. 없으면 raw rtabmap.db로 fallback.
     */
    private Path sourceDbForMerge(String storagePath) {
        Path dir = resolveSourceDir(storagePath);
        Path reprocessed = dir.resolve("rtabmap_reprocessed.db");
        if (java.nio.file.Files.exists(reprocessed)) {
            return reprocessed;
        }
        return dir.resolve("rtabmap.db");
    }

    private Path resolveSourceDir(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null && "rtabmap.db".equals(fileName.toString())) {
            return path.getParent();
        }
        if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            return path.getParent();
        }
        return path;
    }
}
