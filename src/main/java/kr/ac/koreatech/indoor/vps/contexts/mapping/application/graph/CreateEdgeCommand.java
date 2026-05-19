package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;

public record CreateEdgeCommand(
        UUID fromNodeId,
        UUID toNodeId,
        Optional<EdgeType> edgeType
) {
}
