package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.UUID;

public record PathfindingCommand(
        UUID startScanId,
        UUID startAreaId,
        Integer startFloorLevel,
        double startX,
        double startY,
        double startZ,
        UUID destinationId,
        String destinationName
) {
    public PathfindingCommand(UUID startScanId, Integer startFloorLevel,
            double startX, double startY, double startZ, String destinationName) {
        this(startScanId, null, startFloorLevel, startX, startY, startZ, null, destinationName);
    }
}
