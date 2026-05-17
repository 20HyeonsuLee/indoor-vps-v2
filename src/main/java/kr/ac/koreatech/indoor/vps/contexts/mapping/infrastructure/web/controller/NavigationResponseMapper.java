package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.NavigationGeometry;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.stereotype.Component;

@Component
public class NavigationResponseMapper {
    public FloorMapNode floorMapNode(MapNodeEntity node) {
        return new FloorMapNode(
                node.getNodeId(),
                node.getNodeType().name(),
                NavigationGeometry.x(node.getGeom()),
                NavigationGeometry.y(node.getGeom()),
                NavigationGeometry.z(node.getGeom()),
                node.getLabel(),
                null
        );
    }

    public FloorMapEdge floorMapEdge(MapEdgeEntity edge) {
        return new FloorMapEdge(
                edge.getEdgeId(),
                edge.getFromNodeId(),
                edge.getToNodeId(),
                edge.getLengthM(),
                edge.getEdgeType().name()
        );
    }

    public Map<String, Object> nodeMap(MapNodeEntity node) {
        return metadata(
                "id", node.getNodeId(),
                "type", node.getNodeType().name(),
                "x", NavigationGeometry.x(node.getGeom()),
                "y", NavigationGeometry.y(node.getGeom()),
                "z", NavigationGeometry.z(node.getGeom()),
                "label", node.getLabel()
        );
    }

    public Map<String, Object> nodeMap(RouteNode node) {
        return metadata(
                "id", node.id(),
                "type", node.type().name(),
                "x", node.position().x(),
                "y", node.position().y(),
                "z", node.position().z(),
                "label", node.label()
        );
    }

    public Map<String, Object> edgeMap(MapEdgeEntity edge) {
        return metadata(
                "id", edge.getEdgeId(),
                "fromId", edge.getFromNodeId(),
                "toId", edge.getToNodeId(),
                "lengthM", edge.getLengthM(),
                "type", edge.getEdgeType().name()
        );
    }

    public Map<String, Object> edgeMap(RouteEdge edge) {
        return metadata(
                "id", edge.id(),
                "fromId", edge.fromId(),
                "toId", edge.toId(),
                "lengthM", edge.lengthM(),
                "type", edge.type().name()
        );
    }

    public FloorMapBounds floorMapBounds(List<MapNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return new FloorMapBounds(0, 0, 0, 0, 0, 0);
        }
        Bounds bounds = computeBounds(nodes);
        return new FloorMapBounds(
                bounds.minX(),
                bounds.minY(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxX() - bounds.minX(),
                bounds.maxY() - bounds.minY()
        );
    }

    public Optional<Map<String, Double>> pathBounds(List<MapNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        Bounds bounds = computeBounds(nodes);
        return Optional.of(Map.of(
                "minX", bounds.minX(),
                "minY", bounds.minY(),
                "maxX", bounds.maxX(),
                "maxY", bounds.maxY(),
                "widthM", bounds.maxX() - bounds.minX(),
                "heightM", bounds.maxY() - bounds.minY()
        ));
    }

    public Map<String, Object> metadata(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private Bounds computeBounds(List<MapNodeEntity> nodes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MapNodeEntity node : nodes) {
            minX = Math.min(minX, NavigationGeometry.x(node.getGeom()));
            minY = Math.min(minY, NavigationGeometry.y(node.getGeom()));
            maxX = Math.max(maxX, NavigationGeometry.x(node.getGeom()));
            maxY = Math.max(maxY, NavigationGeometry.y(node.getGeom()));
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }
}
