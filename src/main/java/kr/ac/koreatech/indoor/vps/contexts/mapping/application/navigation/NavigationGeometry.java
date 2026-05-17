package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

public final class NavigationGeometry {
    private NavigationGeometry() {
    }

    public static double x(Point point) {
        return point.getX();
    }

    public static double y(Point point) {
        return point.getY();
    }

    public static double z(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return Double.isNaN(coordinate.getZ()) ? 0.0 : coordinate.getZ();
    }

    public static double distance(Point3 a, Point3 b) {
        return a.distanceTo(b);
    }
}
