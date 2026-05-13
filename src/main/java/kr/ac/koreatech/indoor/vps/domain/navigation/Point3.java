package kr.ac.koreatech.indoor.vps.domain.navigation;

public record Point3(double x, double y, double z) {
    public double distanceTo(Point3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
