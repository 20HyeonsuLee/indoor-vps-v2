package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

class GeoJsonConverterTest {

    private final GeoJsonConverter converter = new GeoJsonConverter();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    void emptyList_producesEmptyFeatureCollection() {
        Map<String, Object> fc = converter.toFeatureCollection(List.of());

        assertThat(fc).containsEntry("type", "FeatureCollection");
        assertThat((List<?>) fc.get("features")).isEmpty();
    }

    @Test
    void singlePolygon_mapsCoordinatesAndProperties() {
        FloorAreaPolygonEntity entity = buildEntity(
                UUID.randomUUID(), "session-1", List.of(10L, 20L),
                new Coordinate[]{
                        new Coordinate(1.0, 2.0, 3.0),
                        new Coordinate(4.0, 5.0, 3.0),
                        new Coordinate(7.0, 8.0, 3.0),
                        new Coordinate(1.0, 2.0, 3.0)
                }
        );

        Map<String, Object> fc = converter.toFeatureCollection(List.of(entity));

        List<?> features = (List<?>) fc.get("features");
        assertThat(features).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> feature = (Map<String, Object>) features.get(0);
        assertThat(feature).containsEntry("type", "Feature");

        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
        assertThat(geometry).containsEntry("type", "Polygon");

        @SuppressWarnings("unchecked")
        List<List<List<Double>>> coordinates = (List<List<List<Double>>>) geometry.get("coordinates");
        assertThat(coordinates).hasSize(1);
        List<List<Double>> ring = coordinates.get(0);
        assertThat(ring).hasSize(4);
        assertThat(ring.get(0)).containsExactly(1.0, 2.0);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) feature.get("properties");
        assertThat(props).containsEntry("markSessionId", "session-1");
        assertThat(props.get("areaId")).isEqualTo(entity.getAreaId().toString());
        List<Long> sourceMarkIds = List.of(10L, 20L);
        assertThat((List<?>) props.get("sourceMarkIds")).isEqualTo(sourceMarkIds);
    }

    @Test
    void zCoordinateIsDropped_onlyXY() {
        FloorAreaPolygonEntity entity = buildEntity(
                UUID.randomUUID(), "s", List.of(),
                new Coordinate[]{
                        new Coordinate(3.0, 4.0, 99.0),
                        new Coordinate(5.0, 6.0, 99.0),
                        new Coordinate(7.0, 8.0, 99.0),
                        new Coordinate(3.0, 4.0, 99.0)
                }
        );

        Map<String, Object> fc = converter.toFeatureCollection(List.of(entity));

        @SuppressWarnings("unchecked")
        List<?> features = (List<?>) fc.get("features");
        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) ((Map<?, ?>) features.get(0)).get("geometry");
        @SuppressWarnings("unchecked")
        List<List<List<Double>>> coords = (List<List<List<Double>>>) geometry.get("coordinates");
        List<Double> firstPt = coords.get(0).get(0);
        assertThat(firstPt).hasSize(2);
    }

    private FloorAreaPolygonEntity buildEntity(
            UUID areaId, String markSessionId, List<Long> sourceMarkIds, Coordinate[] coords
    ) {
        LinearRing ring = geometryFactory.createLinearRing(coords);
        Polygon polygon = geometryFactory.createPolygon(ring, new LinearRing[0]);
        return FloorAreaPolygonEntity.create(
                areaId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                markSessionId,
                polygon,
                sourceMarkIds
        );
    }
}
