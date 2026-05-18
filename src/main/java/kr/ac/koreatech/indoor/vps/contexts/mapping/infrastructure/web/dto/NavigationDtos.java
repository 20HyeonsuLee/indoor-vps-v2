package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NavigationDtos {
    private NavigationDtos() {
    }

    public enum RoutePreference {
        SHORTEST,
        ELEVATOR_FIRST,
        STAIRCASE_FIRST
    }

    public enum VerticalPreference {
        ELEVATOR,
        STAIRS
    }

    public record RoutePosition(double x, double y, double z, Integer floorLevel) {
    }

    public record PathStepResponse(
            int stepNumber,
            Integer floorLevel,
            RoutePosition position,
            String instruction,
            UUID nodeId
    ) {
    }

    public record FloorTransitionResponse(
            Integer fromFloorLevel,
            Integer toFloorLevel,
            String connectorType,
            String connectorKey
    ) {
    }

    public record PathfindingRequest(
            UUID startScanId,
            UUID startAreaId,
            Integer startFloorLevel,
            double startX,
            double startY,
            double startZ,
            UUID destinationId,
            String destinationName,
            RoutePreference preference,
            VerticalPreference verticalPreference
    ) {
    }

    public record PathfindingResponse(
            UUID buildingId,
            double totalDistance,
            int estimatedTimeSeconds,
            List<PathStepResponse> steps,
            List<FloorTransitionResponse> floorTransitions,
            Map<String, Object> routeMetadata
    ) {
    }
}
