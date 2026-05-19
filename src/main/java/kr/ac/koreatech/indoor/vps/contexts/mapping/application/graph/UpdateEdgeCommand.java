package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;

public record UpdateEdgeCommand(Optional<EdgeType> edgeType) {
}
