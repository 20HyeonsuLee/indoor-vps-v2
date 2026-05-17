package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.UUID;

public record PathfindingCommand(
        UUID startScanId,
        Integer startFloorLevel,
        double startX,
        double startY,
        double startZ,
        String destinationName
) {
}
