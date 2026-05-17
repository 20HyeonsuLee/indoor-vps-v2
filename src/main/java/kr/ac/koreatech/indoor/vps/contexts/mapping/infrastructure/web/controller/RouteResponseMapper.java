package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import org.springframework.stereotype.Component;

@Component
public class RouteResponseMapper {

    public Map<String, Object> nodeMap(RouteNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.id());
        result.put("type", node.type().name());
        result.put("x", node.position().x());
        result.put("y", node.position().y());
        result.put("z", node.position().z());
        result.put("label", node.label());
        return result;
    }

    public Map<String, Object> edgeMap(RouteEdge edge) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", edge.id());
        result.put("fromId", edge.fromId());
        result.put("toId", edge.toId());
        result.put("lengthM", edge.lengthM());
        result.put("type", edge.type().name());
        return result;
    }

    public Optional<Map<String, Double>> pathBounds(List<RouteNode> nodes) {
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (RouteNode node : nodes) {
            minX = Math.min(minX, node.position().x());
            minY = Math.min(minY, node.position().y());
            maxX = Math.max(maxX, node.position().x());
            maxY = Math.max(maxY, node.position().y());
        }
        return Optional.of(Map.of(
                "minX", minX,
                "minY", minY,
                "maxX", maxX,
                "maxY", maxY,
                "widthM", maxX - minX,
                "heightM", maxY - minY
        ));
    }
}
