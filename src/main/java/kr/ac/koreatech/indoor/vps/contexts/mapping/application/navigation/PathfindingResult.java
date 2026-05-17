package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PathfindingResult(
        UUID buildingId,
        double totalDistance,
        int estimatedTimeSeconds,
        List<PathStep> steps,
        List<FloorTransition> floorTransitions,
        Map<String, Object> routeMetadata
) {
    public record RoutePosition(double x, double y, double z, Integer floorLevel) {
    }

    public record PathStep(
            int stepNumber,
            Integer floorLevel,
            RoutePosition position,
            String instruction,
            UUID nodeId
    ) {
    }

    public record FloorTransition(
            Integer fromFloorLevel,
            Integer toFloorLevel,
            String connectorType,
            String connectorKey
    ) {
    }
}
