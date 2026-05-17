package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.stereotype.Component;

@Component
public class RouteGraphMapper {
    public List<RouteNode> toRouteNodes(List<MapNodeEntity> nodes) {
        return nodes.stream()
                .map(node -> new RouteNode(
                        node.getNodeId(),
                        node.getNodeType(),
                        new Point3(
                                NavigationGeometry.x(node.getGeom()),
                                NavigationGeometry.y(node.getGeom()),
                                NavigationGeometry.z(node.getGeom())
                        ),
                        node.getLabel()
                ))
                .toList();
    }

    public List<RouteEdge> toRouteEdges(List<MapEdgeEntity> edges) {
        return edges.stream()
                .map(edge -> new RouteEdge(
                        edge.getEdgeId(),
                        edge.getFromNodeId(),
                        edge.getToNodeId(),
                        edge.getLengthM(),
                        edge.getEdgeType()
                ))
                .toList();
    }
}
