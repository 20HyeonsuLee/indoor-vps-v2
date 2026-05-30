package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;

public record CreateNodeCommand(
        double x,
        double y,
        double z,
        NodeType nodeType,
        String label
) {
}
