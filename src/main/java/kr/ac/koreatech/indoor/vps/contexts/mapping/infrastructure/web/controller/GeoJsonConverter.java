package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Component;

@Component
public class GeoJsonConverter {

    public Map<String, Object> toFeatureCollection(List<FloorAreaPolygonEntity> polygons) {
        List<Map<String, Object>> features = polygons.stream()
                .map(this::toPolygonFeature)
                .toList();
        return Map.of("type", "FeatureCollection", "features", features);
    }

    private Map<String, Object> toPolygonFeature(FloorAreaPolygonEntity entity) {
        List<List<Double>> ring = exteriorRing(entity);
        return Map.of(
                "type", "Feature",
                "geometry", Map.of(
                        "type", "Polygon",
                        "coordinates", List.of(ring)
                ),
                "properties", properties(entity)
        );
    }

    private List<List<Double>> exteriorRing(FloorAreaPolygonEntity entity) {
        Coordinate[] coords = entity.getPolygon().getExteriorRing().getCoordinates();
        List<List<Double>> ring = new ArrayList<>(coords.length);
        for (Coordinate c : coords) {
            ring.add(List.of(c.x, c.y));
        }
        return ring;
    }

    private Map<String, Object> properties(FloorAreaPolygonEntity entity) {
        return Map.of(
                "areaId", entity.getAreaId().toString(),
                "markSessionId", entity.getMarkSessionId(),
                "sourceMarkIds", entity.getSourceMarkIds() != null ? entity.getSourceMarkIds() : List.of()
        );
    }
}
