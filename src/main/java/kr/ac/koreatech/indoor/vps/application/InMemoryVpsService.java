package kr.ac.koreatech.indoor.vps.application;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "memory")
public class InMemoryVpsService implements VpsService {
    private final IndoorProperties properties;
    private final Map<UUID, BuildingRecord> buildings = new ConcurrentHashMap<>();
    private final Map<UUID, FloorRecord> floors = new ConcurrentHashMap<>();
    private final Map<UUID, ScanRecord> scans = new ConcurrentHashMap<>();
    private final Map<UUID, PoiRecord> pois = new ConcurrentHashMap<>();
    private final AtomicInteger uploadOrder = new AtomicInteger();

    public InMemoryVpsService(IndoorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<BuildingResponse> listBuildings(String statusFilter) {
        return buildings.values().stream()
                .filter(building -> statusFilter == null || building.status().name().equals(statusFilter))
                .sorted(Comparator.comparing(BuildingRecord::createdAt))
                .map(this::toBuildingResponse)
                .toList();
    }

    @Override
    public BuildingResponse createBuilding(BuildingCreateRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        BuildingRecord record = new BuildingRecord(
                id,
                request.name(),
                request.description(),
                request.latitude(),
                request.longitude(),
                BuildingStatus.DRAFT,
                now,
                now
        );
        buildings.put(id, record);
        return toBuildingResponse(record);
    }

    @Override
    public BuildingDetailResponse getBuilding(UUID buildingId) {
        BuildingRecord building = requireBuilding(buildingId);
        return new BuildingDetailResponse(
                building.id(),
                building.name(),
                building.description(),
                building.latitude(),
                building.longitude(),
                building.status(),
                building.createdAt(),
                building.updatedAt(),
                listFloors(building.id()),
                List.of()
        );
    }

    @Override
    public BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request) {
        BuildingRecord current = requireBuilding(buildingId);
        BuildingRecord updated = new BuildingRecord(
                current.id(),
                request.name() != null ? request.name() : current.name(),
                request.description() != null ? request.description() : current.description(),
                request.latitude() != null ? request.latitude() : current.latitude(),
                request.longitude() != null ? request.longitude() : current.longitude(),
                current.status(),
                current.createdAt(),
                Instant.now()
        );
        buildings.put(buildingId, updated);
        return toBuildingResponse(updated);
    }

    @Override
    public void deleteBuilding(UUID buildingId) {
        requireBuilding(buildingId);
        buildings.remove(buildingId);
        floors.values().removeIf(floor -> floor.buildingId().equals(buildingId));
        pois.values().removeIf(poi -> buildingId.equals(poi.buildingId()));
    }

    @Override
    public BuildingResponse patchStatus(UUID buildingId, BuildingStatusRequest request) {
        BuildingRecord current = requireBuilding(buildingId);
        BuildingRecord updated = new BuildingRecord(
                current.id(),
                current.name(),
                current.description(),
                current.latitude(),
                current.longitude(),
                request.status(),
                current.createdAt(),
                Instant.now()
        );
        buildings.put(buildingId, updated);
        return toBuildingResponse(updated);
    }

    @Override
    public List<FloorResponse> listFloors(UUID buildingId) {
        requireBuilding(buildingId);
        return floors.values().stream()
                .filter(floor -> floor.buildingId().equals(buildingId))
                .sorted(Comparator.comparingInt(FloorRecord::level))
                .map(this::toFloorResponse)
                .toList();
    }

    @Override
    public FloorResponse createFloor(UUID buildingId, FloorCreateRequest request) {
        requireBuilding(buildingId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        FloorRecord floor = new FloorRecord(
                id,
                buildingId,
                request.name(),
                request.level(),
                request.height(),
                null,
                now,
                now
        );
        floors.put(id, floor);
        return toFloorResponse(floor);
    }

    @Override
    public FloorResponse getFloor(UUID floorId) {
        return toFloorResponse(requireFloor(floorId));
    }

    @Override
    public FloorResponse updateFloor(UUID floorId, FloorUpdateRequest request) {
        FloorRecord current = requireFloor(floorId);
        FloorRecord updated = new FloorRecord(
                current.id(),
                current.buildingId(),
                request.name() != null ? request.name() : current.name(),
                current.level(),
                request.height() != null ? request.height() : current.height(),
                current.activeScanId(),
                current.createdAt(),
                Instant.now()
        );
        floors.put(floorId, updated);
        return toFloorResponse(updated);
    }

    @Override
    public void deleteFloor(UUID floorId) {
        requireFloor(floorId);
        floors.remove(floorId);
        scans.values().removeIf(scan -> scan.floorId().equals(floorId));
        pois.values().removeIf(poi -> floorId.equals(poi.floorId()));
    }

    @Override
    public FloorPathResponse getFloorPath(UUID floorId) {
        FloorRecord floor = requireFloor(floorId);
        return new FloorPathResponse(
                floor.id(),
                floor.activeScanId(),
                null,
                List.of(),
                List.of(),
                null
        );
    }

    @Override
    public FloorMapResponse getFloorMap(UUID floorId) {
        FloorRecord floor = requireFloor(floorId);
        UUID scanId = floor.activeScanId() != null ? floor.activeScanId() : UUID.randomUUID();
        UUID buildJobId = UUID.nameUUIDFromBytes(("floor-map-" + floor.id()).getBytes());
        return new FloorMapResponse(
                floor.id(),
                floor.buildingId(),
                scanId,
                floor.level(),
                floor.name(),
                buildJobId,
                FloorMapCoordinateSystem.worldMeters(),
                new FloorMapBounds(0, 0, 0, 0, 0, 0),
                Map.of("type", "FeatureCollection", "features", List.of()),
                List.of(),
                List.of(),
                buildJobId.toString()
        );
    }

    @Override
    public ScanChunkResponse uploadScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        FloorRecord floor = requireFloor(floorId);
        UUID scanId = parseOrGenerate(scanIdText);
        UUID chunkId = UUID.randomUUID();
        int order = uploadOrder.incrementAndGet();
        long size = upload.getSize();
        String filename = upload.getOriginalFilename();
        Path destination = properties.getStorageRoot()
                .resolve("scans")
                .resolve(scanId.toString())
                .resolve(filename == null || filename.isBlank() ? "upload.bin" : filename);
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = upload.getInputStream()) {
                Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED", e.getMessage());
        }

        ScanRecord scan = new ScanRecord(
                chunkId,
                floorId,
                scanId,
                filename,
                size,
                destination.toString(),
                "UPLOADED",
                true,
                order,
                Instant.now(),
                deviceInfo
        );
        scans.put(chunkId, scan);
        floors.put(floor.id(), floor.withActiveScanId(scanId));
        return toScanChunkResponse(scan);
    }

    @Override
    public List<ScanChunkResponse> listScanChunks(UUID floorId) {
        requireFloor(floorId);
        return scans.values().stream()
                .filter(scan -> scan.floorId().equals(floorId))
                .sorted(Comparator.comparingInt(ScanRecord::uploadOrder))
                .map(this::toScanChunkResponse)
                .toList();
    }

    @Override
    public void deleteScanChunk(UUID floorId, UUID chunkId) {
        requireFloor(floorId);
        ScanRecord scan = scans.get(chunkId);
        if (scan == null || !scan.floorId().equals(floorId)) {
            throw notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found");
        }
        scans.remove(chunkId);
    }

    @Override
    public MergedScanResponse mergeScans(UUID floorId, List<UUID> chunkIds) {
        FloorRecord floor = requireFloor(floorId);
        UUID activeScanId = floor.activeScanId();
        if (chunkIds != null && !chunkIds.isEmpty()) {
            activeScanId = scans.getOrDefault(chunkIds.get(0), new ScanRecord(
                    UUID.randomUUID(), floorId, UUID.randomUUID(), null, null, null,
                    "MISSING", false, 0, Instant.now(), null
            )).scanId();
            floors.put(floor.id(), floor.withActiveScanId(activeScanId));
        }
        return new MergedScanResponse(floorId, activeScanId, "MERGED");
    }

    @Override
    public MergedScanResponse mergeStatus(UUID floorId) {
        FloorRecord floor = requireFloor(floorId);
        return new MergedScanResponse(floorId, floor.activeScanId(), floor.activeScanId() == null ? "IDLE" : "MERGED");
    }

    @Override
    public ProcessingStatusResponse process(UUID floorId) {
        FloorRecord floor = requireFloor(floorId);
        return new ProcessingStatusResponse(floorId, floor.activeScanId(), UUID.randomUUID(), "QUEUED", 0.0, null);
    }

    @Override
    public ProcessingStatusResponse processStatus(UUID floorId) {
        FloorRecord floor = requireFloor(floorId);
        return new ProcessingStatusResponse(floorId, floor.activeScanId(), null, "IDLE", null, null);
    }

    @Override
    public PathfindingResponse pathfinding(UUID buildingId, PathfindingRequest request) {
        requireBuilding(buildingId);
        RoutePosition position = new RoutePosition(
                request.startX(),
                request.startY(),
                request.startZ(),
                request.startFloorLevel()
        );
        return new PathfindingResponse(
                buildingId,
                0.0,
                0,
                List.of(new PathStepResponse(1, request.startFloorLevel(), position, "Start", null)),
                List.of(),
                Map.of(
                        "destinationName", request.destinationName(),
                        "verticalPreference", Objects.toString(request.verticalPreference(), "ELEVATOR")
                )
        );
    }

    @Override
    public Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        requireFloor(floorId);
        Map<String, Object> route = new HashMap<>();
        route.put("floorId", floorId);
        route.put("from", fromNode);
        route.put("to", toNode);
        route.put("nodes", List.of());
        route.put("edges", List.of());
        return route;
    }

    @Override
    public List<POIResponse> listPois(UUID buildingId) {
        requireBuilding(buildingId);
        return pois.values().stream()
                .filter(poi -> buildingId.equals(poi.buildingId()))
                .map(this::toPoiResponse)
                .toList();
    }

    @Override
    public List<POIResponse> searchPois(UUID buildingId, String query) {
        String q = query == null ? "" : query.toLowerCase();
        return listPois(buildingId).stream()
                .filter(poi -> q.isBlank()
                        || contains(poi.name(), q)
                        || contains(poi.label(), q)
                        || contains(poi.category(), q))
                .toList();
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private BuildingRecord requireBuilding(UUID buildingId) {
        BuildingRecord building = buildings.get(buildingId);
        if (building == null) {
            throw notFound("BUILDING_NOT_FOUND", "building not found");
        }
        return building;
    }

    private FloorRecord requireFloor(UUID floorId) {
        FloorRecord floor = floors.get(floorId);
        if (floor == null) {
            throw notFound("FLOOR_NOT_FOUND", "floor not found");
        }
        return floor;
    }

    private ClientApiException notFound(String code, String message) {
        return new ClientApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private UUID parseOrGenerate(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCAN_ID", "invalid scan_id");
        }
    }

    private BuildingResponse toBuildingResponse(BuildingRecord record) {
        return new BuildingResponse(
                record.id(),
                record.name(),
                record.description(),
                record.latitude(),
                record.longitude(),
                record.status(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private FloorResponse toFloorResponse(FloorRecord record) {
        return new FloorResponse(
                record.id(),
                record.buildingId(),
                record.name(),
                record.level(),
                record.height(),
                record.activeScanId() != null,
                false,
                record.activeScanId(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private ScanChunkResponse toScanChunkResponse(ScanRecord record) {
        return new ScanChunkResponse(
                record.chunkId(),
                record.floorId(),
                record.scanId(),
                record.fileName(),
                record.fileSize(),
                record.status(),
                record.active(),
                record.uploadOrder(),
                record.createdAt()
        );
    }

    private POIResponse toPoiResponse(PoiRecord record) {
        return new POIResponse(
                record.poiId(),
                record.buildingId(),
                record.floorId(),
                record.name(),
                record.label(),
                record.category(),
                record.routeNodeId(),
                record.displayPoint(),
                record.needsReview(),
                record.llmConfidence()
        );
    }

    private record BuildingRecord(
            UUID id,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record FloorRecord(
            UUID id,
            UUID buildingId,
            String name,
            int level,
            Double height,
            UUID activeScanId,
            Instant createdAt,
            Instant updatedAt
    ) {
        FloorRecord withActiveScanId(UUID scanId) {
            return new FloorRecord(id, buildingId, name, level, height, scanId, createdAt, Instant.now());
        }
    }

    private record ScanRecord(
            UUID chunkId,
            UUID floorId,
            UUID scanId,
            String fileName,
            Long fileSize,
            String storagePath,
            String status,
            boolean active,
            int uploadOrder,
            Instant createdAt,
            String deviceInfo
    ) {
    }

    private record PoiRecord(
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
}
