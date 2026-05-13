package kr.ac.koreatech.indoor.vps.application.navigation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

final class NavigationGeometry {
    private NavigationGeometry() {
    }

    static double x(Point point) {
        return point.getX();
    }

    static double y(Point point) {
        return point.getY();
    }

    static double z(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return Double.isNaN(coordinate.getZ()) ? 0.0 : coordinate.getZ();
    }

    static int estimateSeconds(double distanceM) {
        return (int) Math.ceil(distanceM / 1.2);
    }

    static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
