package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.Optional;
import java.util.UUID;

public record UpdatePoiCommand(
        Optional<String> name,
        Optional<String> category,
        Optional<String> label,
        Optional<Double> x,
        Optional<Double> y,
        Optional<Double> z,
        Optional<Double> displayX,
        Optional<Double> displayY,
        Optional<Double> displayZ,
        Optional<UUID> routeNodeId,
        Optional<Boolean> detachRouteNode,
        Optional<Boolean> markReviewed
) {
}
