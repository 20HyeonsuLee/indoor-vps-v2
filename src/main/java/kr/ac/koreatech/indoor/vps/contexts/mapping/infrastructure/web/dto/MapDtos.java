package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MapDtos {
    private MapDtos() {
    }

    public record FloorPathResponse(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            Map<String, Double> bounds
    ) {
    }

    public record FloorMapCoordinateSystem(String frame, String description) {
        public static FloorMapCoordinateSystem worldMeters() {
            return new FloorMapCoordinateSystem(
                    "world_xy_meters",
                    "Server world frame. (x, y) is the floor plane in meters; z is height."
            );
        }
    }

    public record FloorMapBounds(
            double minX,
            double minY,
            double maxX,
            double maxY,
            double widthM,
            double heightM
    ) {
    }

    public record FloorMapConnector(String type, String key) {
    }

    public record FloorMapNode(
            UUID id,
            String type,
            double x,
            double y,
            double z,
            String label,
            FloorMapConnector connector
    ) {
    }

    public record FloorMapEdge(UUID id, UUID fromId, UUID toId, double lengthM, String type) {
    }

    public record FloorMapResponse(
            UUID floorId,
            UUID buildingId,
            UUID scanId,
            int floorLevel,
            String floorName,
            UUID buildJobId,
            FloorMapCoordinateSystem coordinateSystem,
            FloorMapBounds bounds,
            Map<String, Object> polygon,
            List<FloorMapNode> nodes,
            List<FloorMapEdge> edges,
            String etag
    ) {
    }
}
