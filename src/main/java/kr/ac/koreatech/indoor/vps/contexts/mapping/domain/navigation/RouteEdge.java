package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import java.util.UUID;

public record RouteEdge(
        UUID id,
        UUID fromId,
        UUID toId,
        double lengthM,
        EdgeType type,
        double costM,
        String connectorType,
        String connectorKey
) {
    public RouteEdge(UUID id, UUID fromId, UUID toId, double lengthM, EdgeType type) {
        this(id, fromId, toId, lengthM, type, lengthM, null, null);
    }

    public RouteEdge reversed() {
        return new RouteEdge(id, toId, fromId, lengthM, type, costM, connectorType, connectorKey);
    }
}
