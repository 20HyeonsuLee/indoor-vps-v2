package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import java.util.List;

public record RouteResult(
        List<RouteNode> nodes,
        List<RouteEdge> edges,
        double totalDistance
) {
    public static RouteResult empty() {
        return new RouteResult(List.of(), List.of(), 0.0);
    }
}
