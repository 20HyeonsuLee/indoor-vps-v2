package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

/**
 * POI 좌표 → JTS Point 변환 단일화. UseCase instance_vars 부담 완화용.
 */
@Component
public class PoiGeometryFactory {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public Point point(double x, double y, double z) {
        return geometryFactory.createPoint(new Coordinate(x, y, z));
    }
}
