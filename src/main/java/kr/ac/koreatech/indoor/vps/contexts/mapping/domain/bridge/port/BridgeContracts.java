package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BridgeContracts {
    private BridgeContracts() {
    }

    public record HealthBridgeResponse(boolean ok, List<String> commands, String mlDevice, String cudaVisibleDevices) {
    }

    public record LocalizeBridgeRequest(
            String buildingId,
            List<String> imagePaths,
            List<String> depthPaths,
            String storageRoot,
            List<FloorMapBridgeRef> floorMaps
    ) {
        public LocalizeBridgeRequest(String buildingId, List<String> imagePaths,
                String storageRoot, List<FloorMapBridgeRef> floorMaps) {
            this(buildingId, imagePaths, List.of(), storageRoot, floorMaps);
        }
    }

    public record FloorMapBridgeRef(
            String floorId,
            String areaId,
            String floorName,
            int level,
            String filePath
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizeBridgeResponse(
            Map<String, Object> pose,
            double confidence,
            int numMatches,
            int matchedImageIndex,
            String methodUsed,
            String floorId,
            String areaId,
            int floorLevel
    ) {
    }

    public record MergeScanBridgeRequest(
            UUID floorId,
            UUID scanId,
            List<String> sourcePaths,
            String outputDir
    ) {
    }

    public record MergeScanBridgeResponse(
            String mergedDbPath,
            String sha256,
            long fileSize,
            Map<String, Object> diagnostics
    ) {
    }

    public record BuildSuperpointIndexRequest(
            String scanId,
            String dbPath
    ) {
    }

    public record BuildSuperpointIndexResponse(
            String cacheDir,
            int frameCount,
            long totalKeypoints,
            long bytes,
            long elapsedMs
    ) {
    }
}
