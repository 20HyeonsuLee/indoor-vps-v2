package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class ConnectorDtos {
    private ConnectorDtos() {
    }

    public record ConnectorCreateRequest(
            @NotBlank String connectorType,
            @NotBlank String connectorKey,
            String name
    ) {
    }

    public record ConnectorUpdateRequest(
            String connectorType,
            String connectorKey,
            String name,
            Boolean mock
    ) {
    }

    public record ConnectorStopRequest(
            UUID areaId,
            UUID routeNodeId,
            Boolean detachRouteNode
    ) {
    }
}
