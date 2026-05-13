package kr.ac.koreatech.indoor.vps.application.bridge;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BridgeContracts {
    private BridgeContracts() {
    }

    public record HealthBridgeResponse(boolean ok, List<String> commands) {
    }

    public record LocalizeBridgeRequest(
            String buildingId,
            List<String> imagePaths,
            String storageRoot
    ) {
    }

    public record LocalizeBridgeResponse(
            Map<String, Object> pose,
            double confidence,
            String mapId,
            int numMatches,
            int matchedImageIndex,
            String floorId,
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

    public record BuildFloorMapBridgeRequest(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            String scanPath,
            String outputDir
    ) {
    }

    public record BuildFloorMapBridgeResponse(
            String artifactPath,
            Map<String, Object> diagnostics
    ) {
    }
}
