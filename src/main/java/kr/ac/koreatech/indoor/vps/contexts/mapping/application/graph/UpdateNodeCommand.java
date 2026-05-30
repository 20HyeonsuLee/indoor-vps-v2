package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;

public record UpdateNodeCommand(
        Optional<Double> x,
        Optional<Double> y,
        Optional<Double> z,
        Optional<NodeType> nodeType,
        Optional<String> label
) {
}
