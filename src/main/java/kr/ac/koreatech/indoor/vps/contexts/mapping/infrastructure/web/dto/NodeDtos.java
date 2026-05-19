package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.NodeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;

public final class NodeDtos {
    private NodeDtos() {
    }

    public record NodeCreateRequest(
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double z,
            @NotNull NodeType nodeType,
            String label
    ) {
    }

    public record NodeUpdateRequest(
            Double x,
            Double y,
            Double z,
            NodeType nodeType,
            String label
    ) {
    }

    public record NodeResponse(
            UUID nodeId,
            UUID areaId,
            NodeType nodeType,
            double x,
            double y,
            double z,
            String label,
            boolean stale,
            String origin
    ) {
        public static NodeResponse from(NodeResult result) {
            return new NodeResponse(
                    result.nodeId(),
                    result.areaId(),
                    result.nodeType(),
                    result.x(),
                    result.y(),
                    result.z(),
                    result.label(),
                    result.stale(),
                    result.origin()
            );
        }
    }
}
