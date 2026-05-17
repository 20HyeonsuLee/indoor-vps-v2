package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import java.util.UUID;

public record RouteNode(
        UUID id,
        NodeType type,
        Point3 position,
        String label
) {
}
