package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import java.util.UUID;

public record RouteEdge(
        UUID id,
        UUID fromId,
        UUID toId,
        double lengthM,
        EdgeType type
) {
    public RouteEdge reversed() {
        return new RouteEdge(id, toId, fromId, lengthM, type);
    }
}
