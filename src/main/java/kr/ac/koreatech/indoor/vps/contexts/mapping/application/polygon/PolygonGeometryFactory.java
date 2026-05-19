package kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon;

import java.util.List;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PolygonGeometryFactory {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public Polygon polygon(List<PolygonCommand.Vertex> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new ClientApiException(
                    HttpStatus.BAD_REQUEST, "POLYGON_TOO_FEW_VERTICES",
                    "polygon requires at least 3 vertices");
        }
        Coordinate[] coords = new Coordinate[vertices.size() + 1];
        for (int i = 0; i < vertices.size(); i++) {
            PolygonCommand.Vertex v = vertices.get(i);
            coords[i] = new Coordinate(v.x(), v.y(), v.z());
        }
        coords[vertices.size()] = new Coordinate(coords[0]);
        LinearRing ring = geometryFactory.createLinearRing(coords);
        return geometryFactory.createPolygon(ring);
    }
}
