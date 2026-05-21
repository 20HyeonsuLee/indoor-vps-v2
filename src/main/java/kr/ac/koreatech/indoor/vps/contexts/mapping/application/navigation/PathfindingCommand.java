package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.UUID;

public record PathfindingCommand(
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
    public PathfindingCommand {
        if (preference == null) {
            preference = RoutePreference.SHORTEST;
        }
    }

    public PathfindingCommand(Integer startFloorLevel,
            double startX, double startY, double startZ, String destinationName) {
        this(null, startFloorLevel, startX, startY, startZ, null, destinationName,
                RoutePreference.SHORTEST, null);
    }

    public VerticalPreference effectiveVerticalPreference() {
        if (verticalPreference != null) {
            return verticalPreference;
        }
        return switch (preference) {
            case ELEVATOR_FIRST -> VerticalPreference.ELEVATOR;
            case STAIRCASE_FIRST -> VerticalPreference.STAIRS;
            case SHORTEST -> null;
        };
    }
}
