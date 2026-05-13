package kr.ac.koreatech.indoor.vps.application.persistence;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class NavigationGraphService {
    public UUID nearestNode(List<MapNodeEntity> nodes, double targetX, double targetY, double targetZ) {
        return nodes.stream()
                .min(Comparator.comparingDouble(node -> distance(
                        x(node.getGeom()),
                        y(node.getGeom()),
                        z(node.getGeom()),
                        targetX,
                        targetY,
                        targetZ
                )))
                .map(MapNodeEntity::getNodeId)
                .orElse(null);
    }

    public RouteResult routeBetween(
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            UUID fromNode,
            UUID toNode
    ) {
        Map<UUID, MapNodeEntity> nodeById = new HashMap<>();
        for (MapNodeEntity node : nodes) {
            nodeById.put(node.getNodeId(), node);
        }
        if (!nodeById.containsKey(fromNode) || !nodeById.containsKey(toNode)) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "ROUTE_NODE_NOT_FOUND", "route node not found");
        }

        SimpleWeightedGraph<UUID, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        nodes.forEach(node -> graph.addVertex(node.getNodeId()));
        Map<DefaultWeightedEdge, RouteEdge> edgeByGraphEdge = new HashMap<>();
        for (MapEdgeEntity edge : edges) {
            DefaultWeightedEdge graphEdge = graph.addEdge(edge.getFromNodeId(), edge.getToNodeId());
            if (graphEdge == null) {
                graphEdge = graph.getEdge(edge.getFromNodeId(), edge.getToNodeId());
                if (graphEdge == null || graph.getEdgeWeight(graphEdge) <= edge.getLengthM()) {
                    continue;
                }
            }
            graph.setEdgeWeight(graphEdge, edge.getLengthM());
            edgeByGraphEdge.put(graphEdge, new RouteEdge(
                    edge.getEdgeId(),
                    edge.getFromNodeId(),
                    edge.getToNodeId(),
                    edge.getLengthM(),
                    edge.getEdgeType().name()
            ));
        }

        GraphPath<UUID, DefaultWeightedEdge> path = DijkstraShortestPath.findPathBetween(graph, fromNode, toNode);
        if (path == null) {
            return new RouteResult(List.of(), List.of(), 0.0);
        }

        ArrayDeque<MapNodeEntity> routeNodes = new ArrayDeque<>();
        ArrayDeque<RouteEdge> routeEdges = new ArrayDeque<>();
        List<UUID> vertexList = path.getVertexList();
        for (UUID vertex : vertexList) {
            routeNodes.addLast(nodeById.get(vertex));
        }
        List<DefaultWeightedEdge> edgeList = path.getEdgeList();
        for (int i = 0; i < edgeList.size(); i++) {
            RouteEdge edge = edgeByGraphEdge.get(edgeList.get(i));
            UUID from = vertexList.get(i);
            UUID to = vertexList.get(i + 1);
            if (!edge.fromId().equals(from) || !edge.toId().equals(to)) {
                edge = edge.reverse();
            }
            routeEdges.addLast(edge);
        }
        return new RouteResult(List.copyOf(routeNodes), List.copyOf(routeEdges), path.getWeight());
    }

    private double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double x(Point point) {
        return point.getX();
    }

    private double y(Point point) {
        return point.getY();
    }

    private double z(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return Double.isNaN(coordinate.getZ()) ? 0.0 : coordinate.getZ();
    }

    public record RouteEdge(UUID id, UUID fromId, UUID toId, double lengthM, String type) {
        public RouteEdge reverse() {
            return new RouteEdge(id, toId, fromId, lengthM, type);
        }
    }

    public record RouteResult(List<MapNodeEntity> nodes, List<RouteEdge> edges, double totalDistance) {
    }
}
