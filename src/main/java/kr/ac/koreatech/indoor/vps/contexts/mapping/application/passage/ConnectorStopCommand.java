package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.Optional;
import java.util.UUID;

public record ConnectorStopCommand(
        Optional<UUID> areaId,
        Optional<UUID> routeNodeId,
        Optional<Boolean> detachRouteNode
) {
}
