package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.NavigationGeometry;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.stereotype.Component;

@Component
public class FloorMapResponseMapper {

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

    public Map<String, Object> nodeMap(MapNodeEntity node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.getNodeId());
        result.put("type", node.getNodeType().name());
        result.put("x", NavigationGeometry.x(node.getGeom()));
        result.put("y", NavigationGeometry.y(node.getGeom()));
        result.put("z", NavigationGeometry.z(node.getGeom()));
        result.put("label", node.getLabel());
        return result;
    }

    public Map<String, Object> edgeMap(MapEdgeEntity edge) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", edge.getEdgeId());
        result.put("fromNodeId", edge.getFromNodeId());
        result.put("toNodeId", edge.getToNodeId());
        result.put("lengthM", edge.getLengthM());
        result.put("type", edge.getEdgeType().name());
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
