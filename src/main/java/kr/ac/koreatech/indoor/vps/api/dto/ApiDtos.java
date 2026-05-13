package kr.ac.koreatech.indoor.vps.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public enum BuildingStatus {
        DRAFT,
        ACTIVE
    }

    public enum RoutePreference {
        SHORTEST,
        ELEVATOR_FIRST,
        STAIRCASE_FIRST
    }

    public enum VerticalPreference {
        ELEVATOR,
        STAIRS
    }

    public record ClientApiErrorResponse(String code, String message, Map<String, Object> detail) {
    }

    public record BuildingCreateRequest(
            @NotBlank String name,
            String description,
            Double latitude,
            Double longitude
    ) {
    }

    public record BuildingUpdateRequest(
            String name,
            String description,
            Double latitude,
            Double longitude
    ) {
    }

    public record BuildingStatusRequest(@NotNull BuildingStatus status) {
    }

    public record BuildingResponse(
            UUID buildingId,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record BuildingDetailResponse(
            UUID buildingId,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<FloorResponse> floors,
            List<VerticalPassageResponse> verticalPassages
    ) {
    }

    public record FloorCreateRequest(@NotBlank String name, int level, Double height) {
    }

    public record FloorUpdateRequest(String name, Double height) {
    }

    public record FloorResponse(
            UUID floorId,
            UUID buildingId,
            String name,
            int level,
            Double height,
            boolean hasPath,
            boolean hasPly,
            UUID activeScanId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PassageSegment(
            String stopId,
            String levelId,
            String routeNodeId,
            Double x,
            Double y,
            String floorId,
            String kind
    ) {
    }

    public record VerticalPassageResponse(
            UUID passageId,
            UUID buildingId,
            String connectorType,
            String connectorKey,
            String name,
            boolean mock,
            List<PassageSegment> segments
    ) {
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

    public record FloorMapBounds(double minX, double minY, double maxX, double maxY,
                                 double widthM, double heightM) {
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

    public record RoutePosition(double x, double y, double z, Integer floorLevel) {
    }

    public record PathStepResponse(
            int stepNumber,
            Integer floorLevel,
            RoutePosition position,
            String instruction,
            UUID nodeId
    ) {
    }

    public record FloorTransitionResponse(
            Integer fromFloorLevel,
            Integer toFloorLevel,
            String connectorType,
            String connectorKey
    ) {
    }

    public record PathfindingRequest(
            UUID startScanId,
            Integer startFloorLevel,
            double startX,
            double startY,
            double startZ,
            @NotBlank String destinationName,
            RoutePreference preference,
            VerticalPreference verticalPreference
    ) {
    }

    public record PathfindingResponse(
            UUID buildingId,
            double totalDistance,
            int estimatedTimeSeconds,
            List<PathStepResponse> steps,
            List<FloorTransitionResponse> floorTransitions,
            Map<String, Object> routeMetadata
    ) {
    }

    public record MergeScansRequest(List<UUID> chunkIds) {
    }

    public record ScanChunkResponse(
            UUID chunkId,
            UUID floorId,
            UUID scanId,
            String fileName,
            Long fileSize,
            String status,
            boolean active,
            int uploadOrder,
            Instant createdAt
    ) {
    }

    public record MergedScanResponse(UUID floorId, UUID activeScanId, String status) {
    }

    public record ProcessingStatusResponse(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            String status,
            Double progress,
            String error
    ) {
    }

    public record POIResponse(
            UUID poiId,
            UUID buildingId,
            UUID floorId,
            String name,
            String label,
            String category,
            UUID routeNodeId,
            Map<String, Double> displayPoint,
            boolean needsReview,
            Double llmConfidence
    ) {
    }

    public record SLAMLocalizeResponse(
            Map<String, Object> pose,
            double confidence,
            String mapId,
            int numMatches,
            int matchedImageIndex,
            String floorId,
            int floorLevel
    ) {
    }
}
