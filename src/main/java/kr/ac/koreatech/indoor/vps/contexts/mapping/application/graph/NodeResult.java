package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;

public record NodeResult(
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
    public static NodeResult from(MapNodeEntity node) {
        String origin = node.isManual() ? "manual_edit" : "scan";
        return new NodeResult(
                node.getNodeId(),
                node.getAreaId(),
                node.getNodeType(),
                node.getGeom().getX(),
                node.getGeom().getY(),
                node.getGeom().getCoordinate().getZ(),
                node.getLabel(),
                false,
                origin
        );
    }
}
