package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.UUID;

public record FloorRouteCommand(UUID floorId, UUID fromNode, UUID toNode) {
}
