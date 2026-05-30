package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import org.locationtech.jts.geom.Coordinate;

public record PolygonResult(
        UUID polygonId,
        UUID floorAreaId,
        String markSessionId,
        List<Vertex> vertices
) {
    public record Vertex(double x, double y, double z) {
    }

    public static PolygonResult from(FloorAreaPolygonEntity entity) {
        Coordinate[] coords = entity.getPolygon().getExteriorRing().getCoordinates();
        // 닫힌 ring의 마지막 점(첫 점과 동일) 제외
        int last = coords.length > 0 && coords[0].equals2D(coords[coords.length - 1]) ? coords.length - 1 : coords.length;
        List<Vertex> vertices = new java.util.ArrayList<>(last);
        for (int i = 0; i < last; i++) {
            Coordinate c = coords[i];
            double z = Double.isNaN(c.getZ()) ? 0.0 : c.getZ();
            vertices.add(new Vertex(c.getX(), c.getY(), z));
        }
        return new PolygonResult(
                entity.getAreaId(),
                entity.getFloorArea() == null ? null : entity.getFloorArea().getAreaId(),
                entity.getMarkSessionId(),
                vertices
        );
    }
}
