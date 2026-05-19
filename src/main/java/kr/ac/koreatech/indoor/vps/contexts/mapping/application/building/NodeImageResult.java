package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

public record NodeImageResult(
        long nodeId,
        double x,
        double y,
        double z,
        double distance,
        double cameraAngle,
        String imageUrl
) {
}
