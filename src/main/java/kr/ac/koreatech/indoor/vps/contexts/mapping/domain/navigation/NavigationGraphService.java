package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.common.DomainException;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.stereotype.Service;

@Service
public class NavigationGraphService {
    public UUID nearestNode(List<RouteNode> nodes, Point3 target) {
        return nodes.stream()
                .min(Comparator.comparingDouble(node -> node.position().distanceTo(target)))
                .map(RouteNode::id)
                .orElse(null);
    }

    public int estimateWalkingSeconds(double distanceM) {
        return (int) Math.ceil(distanceM / 1.2);
    }

    public RouteResult routeBetween(
            List<RouteNode> nodes,
            List<RouteEdge> edges,
            UUID fromNode,
            UUID toNode
    ) {
        Map<UUID, RouteNode> nodeById = new HashMap<>();
        for (RouteNode node : nodes) {
            nodeById.put(node.id(), node);
        }
        if (!nodeById.containsKey(fromNode) || !nodeById.containsKey(toNode)) {
            throw new DomainException(404, "ROUTE_NODE_NOT_FOUND", "route node not found");
        }

        SimpleWeightedGraph<UUID, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        nodes.forEach(node -> graph.addVertex(node.id()));
        Map<DefaultWeightedEdge, RouteEdge> edgeByGraphEdge = new HashMap<>();
        for (RouteEdge edge : edges) {
            DefaultWeightedEdge graphEdge = graph.addEdge(edge.fromId(), edge.toId());
            if (graphEdge == null) {
                graphEdge = graph.getEdge(edge.fromId(), edge.toId());
                if (graphEdge == null || graph.getEdgeWeight(graphEdge) <= edge.lengthM()) {
                    continue;
                }
            }
            graph.setEdgeWeight(graphEdge, edge.lengthM());
            edgeByGraphEdge.put(graphEdge, edge);
        }

        GraphPath<UUID, DefaultWeightedEdge> path = DijkstraShortestPath.findPathBetween(graph, fromNode, toNode);
        if (path == null) {
            return RouteResult.empty();
        }

        ArrayDeque<RouteNode> routeNodes = new ArrayDeque<>();
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
                edge = edge.reversed();
            }
            routeEdges.addLast(edge);
        }
        return new RouteResult(List.copyOf(routeNodes), List.copyOf(routeEdges), path.getWeight());
    }
}
