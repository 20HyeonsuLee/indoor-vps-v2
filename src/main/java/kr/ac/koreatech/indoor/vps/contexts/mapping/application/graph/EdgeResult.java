package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;

public record EdgeResult(
        UUID edgeId,
        UUID areaId,
        UUID fromNodeId,
        UUID toNodeId,
        EdgeType edgeType,
        double lengthM,
        Double widthM
) {
    public static EdgeResult from(MapEdgeEntity edge) {
        return new EdgeResult(
                edge.getEdgeId(),
                edge.getAreaId(),
                edge.getFromNodeId(),
                edge.getToNodeId(),
                edge.getEdgeType(),
                edge.getLengthM(),
                edge.getWidthM()
        );
    }
}
