package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.EdgeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;

public final class EdgeDtos {
    private EdgeDtos() {
    }

    public record EdgeCreateRequest(
            @NotNull UUID fromNodeId,
            @NotNull UUID toNodeId,
            EdgeType edgeType
    ) {
    }

    public record EdgeUpdateRequest(
            EdgeType edgeType,
            Double widthM,
            Boolean clearWidth
    ) {
    }

    public record EdgeResponse(
            UUID edgeId,
            UUID areaId,
            UUID fromNodeId,
            UUID toNodeId,
            EdgeType edgeType,
            double lengthM,
            Double widthM
    ) {
        public static EdgeResponse from(EdgeResult result) {
            return new EdgeResponse(
                    result.edgeId(),
                    result.areaId(),
                    result.fromNodeId(),
                    result.toNodeId(),
                    result.edgeType(),
                    result.lengthM(),
                    result.widthM()
            );
        }
    }
}
