package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.Optional;
import java.util.UUID;

public record FloorRouteCommand(UUID floorId, Optional<UUID> areaId, UUID fromNode, UUID toNode) {
    public FloorRouteCommand(UUID floorId, UUID fromNode, UUID toNode) {
        this(floorId, Optional.empty(), fromNode, toNode);
    }
}
