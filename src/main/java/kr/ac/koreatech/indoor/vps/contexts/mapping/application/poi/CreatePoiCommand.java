package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.Optional;
import java.util.UUID;

public record CreatePoiCommand(
        UUID areaId,
        String name,
        String category,
        double x,
        double y,
        double z,
        Optional<Double> displayX,
        Optional<Double> displayY,
        Optional<Double> displayZ,
        Optional<UUID> routeNodeId
) {
}
