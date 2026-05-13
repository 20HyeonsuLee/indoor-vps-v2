package kr.ac.koreatech.indoor.vps.application;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.BuildState;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.ScanIngestRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class JpaVpsService implements VpsService {
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final BuildJobRepository buildJobRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final PoiCanonicalRepository poiCanonicalRepository;
    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    public JpaVpsService(
            BuildingRepository buildingRepository,
            FloorRepository floorRepository,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            BuildJobRepository buildJobRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            PoiCanonicalRepository poiCanonicalRepository,
            IndoorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.buildJobRepository = buildJobRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.poiCanonicalRepository = poiCanonicalRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BuildingResponse> listBuildings(String statusFilter) {
        List<BuildingEntity> buildings = statusFilter == null || statusFilter.isBlank()
                ? buildingRepository.findAllByOrderByCreatedAtAsc()
                : buildingRepository.findByStatusOrderByCreatedAtAsc(statusFilter);
        return buildings.stream().map(this::toBuildingResponse).toList();
    }

    @Override
    @Transactional
    public BuildingResponse createBuilding(BuildingCreateRequest request) {
        BuildingEntity building = new BuildingEntity(
                request.name(),
                request.description(),
                request.latitude(),
                request.longitude()
        );
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    @Override
    public BuildingDetailResponse getBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        return new BuildingDetailResponse(
                building.getBuildingId(),
                building.getName(),
                building.getDescription(),
                building.getLatitude(),
                building.getLongitude(),
                BuildingStatus.valueOf(building.getStatus()),
                building.getCreatedAt(),
                building.getUpdatedAt(),
                listFloors(buildingId),
                List.of()
        );
    }

    @Override
    @Transactional
    public BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        if (request.name() != null) {
            building.setName(request.name());
        }
        if (request.description() != null) {
            building.setDescription(request.description());
        }
        if (request.latitude() != null) {
            building.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            building.setLongitude(request.longitude());
        }
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    @Override
    @Transactional
    public void deleteBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        buildingRepository.delete(building);
        buildingRepository.flush();
    }

    @Override
    @Transactional
    public BuildingResponse patchStatus(UUID buildingId, BuildingStatusRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        building.setStatus(request.status().name());
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    @Override
    public List<FloorResponse> listFloors(UUID buildingId) {
        requireBuilding(buildingId);
        return floorRepository.findByBuilding_BuildingIdOrderByLevelAscNameAsc(buildingId).stream()
                .map(this::toFloorResponse)
                .toList();
    }

    @Override
    @Transactional
    public FloorResponse createFloor(UUID buildingId, FloorCreateRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        try {
            FloorEntity floor = new FloorEntity(building, request.name(), request.level(), request.height());
            return toFloorResponse(floorRepository.saveAndFlush(floor));
        } catch (DataIntegrityViolationException e) {
            throw new ClientApiException(HttpStatus.CONFLICT, "FLOOR_CONFLICT", "floor level already exists");
        }
    }

    @Override
    public FloorResponse getFloor(UUID floorId) {
        return toFloorResponse(requireFloor(floorId));
    }

    @Override
    @Transactional
    public FloorResponse updateFloor(UUID floorId, FloorUpdateRequest request) {
        FloorEntity floor = requireFloor(floorId);
        if (request.name() != null) {
            floor.setName(request.name());
        }
        if (request.height() != null) {
            floor.setHeight(request.height());
        }
        return toFloorResponse(floorRepository.saveAndFlush(floor));
    }

    @Override
    @Transactional
    public void deleteFloor(UUID floorId) {
        FloorEntity floor = requireFloor(floorId);
        floorRepository.delete(floor);
        floorRepository.flush();
    }

    @Override
    public FloorPathResponse getFloorPath(UUID floorId) {
        requireFloor(floorId);
        Optional<FloorScanEntity> active = activeScan(floorId);
        if (active.isEmpty()) {
            return new FloorPathResponse(floorId, null, null, List.of(), List.of(), null);
        }
        UUID scanId = active.get().getScan().getScanId();
        List<MapNodeEntity> nodes = nodes(scanId);
        List<MapEdgeEntity> edges = edges(scanId);
        return new FloorPathResponse(
                floorId,
                scanId,
                latestBuildJobId(scanId),
                nodes.stream().map(this::nodeMap).toList(),
                edges.stream().map(this::edgeMap).toList(),
                pathBounds(nodes)
        );
    }

    @Override
    public FloorMapResponse getFloorMap(UUID floorId) {
        FloorEntity floor = requireFloor(floorId);
        Optional<FloorScanEntity> active = activeScan(floorId);
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        UUID buildJobId = scanId == null ? null : latestBuildJobId(scanId);
        List<MapNodeEntity> nodes = scanId == null ? List.of() : nodes(scanId);
        List<MapEdgeEntity> edges = scanId == null ? List.of() : edges(scanId);
        return new FloorMapResponse(
                floor.getFloorId(),
                floor.getBuilding().getBuildingId(),
                scanId,
                floor.getLevel(),
                floor.getName(),
                buildJobId,
                FloorMapCoordinateSystem.worldMeters(),
                floorMapBounds(nodes),
                Map.of("type", "FeatureCollection", "features", List.of()),
                nodes.stream().map(this::floorMapNode).toList(),
                edges.stream().map(this::floorMapEdge).toList(),
                etagFor(floor.getFloorId(), scanId, buildJobId, nodes.size(), edges.size())
        );
    }

    @Override
    @Transactional
    public ScanChunkResponse uploadScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        FloorEntity floor = requireFloor(floorId);
        UUID scanId = parseOrGenerate(scanIdText);
        String fileName = safeFileName(upload.getOriginalFilename());
        Path destination = properties.getStorageRoot()
                .resolve("scans")
                .resolve(scanId.toString())
                .resolve(fileName);
        StoredUpload stored = storeUpload(upload, destination);

        try {
            ScanIngestEntity scan = scanIngestRepository.findById(scanId)
                    .map(existing -> {
                        if (!force) {
                            throw new ClientApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_EXISTS", "scan_id already exists");
                        }
                        existing.replacePayload(stored.sha256(), stored.path().toString(), deviceInfoMap(deviceInfo));
                        return existing;
                    })
                    .orElseGet(() -> new ScanIngestEntity(
                            scanId,
                            stored.sha256(),
                            stored.path().toString(),
                            deviceInfoMap(deviceInfo)
                    ));
            ScanIngestEntity persistedScan = scanIngestRepository.saveAndFlush(scan);

            floorScanRepository.deactivateForFloor(floorId);
            floorScanRepository.flush();

            FloorScanEntity floorScan = floorScanRepository.findByFloor_FloorIdAndScan_ScanId(floorId, scanId)
                    .orElseGet(() -> new FloorScanEntity(
                            floor,
                            persistedScan,
                            fileName,
                            stored.size(),
                            floorScanRepository.nextUploadOrder(floorId)
                    ));
            floorScan.setFileName(fileName);
            floorScan.setFileSize(stored.size());
            floorScan.setStatus("UPLOADED");
            floorScan.setActive(true);
            floorScan = floorScanRepository.saveAndFlush(floorScan);
            return toScanChunkResponse(floorScan);
        } catch (RuntimeException e) {
            deleteQuietly(destination);
            throw e;
        }
    }

    @Override
    public List<ScanChunkResponse> listScanChunks(UUID floorId) {
        requireFloor(floorId);
        return floorScanRepository.findByFloor_FloorIdOrderByUploadOrderAscCreatedAtAsc(floorId).stream()
                .map(this::toScanChunkResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteScanChunk(UUID floorId, UUID chunkId) {
        requireFloor(floorId);
        FloorScanEntity floorScan = floorScanRepository.findByFloor_FloorIdAndFloorScanId(floorId, chunkId)
                .orElseThrow(() -> notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        UUID scanId = floorScan.getScan().getScanId();
        floorScanRepository.delete(floorScan);
        floorScanRepository.flush();
        if (!floorScanRepository.existsByScan_ScanId(scanId)) {
            scanIngestRepository.deleteById(scanId);
        }
    }

    @Override
    @Transactional
    public MergedScanResponse mergeScans(UUID floorId, List<UUID> chunkIds) {
        requireFloor(floorId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            return mergeStatus(floorId);
        }
        FloorScanEntity target = floorScanRepository.findByFloor_FloorIdAndFloorScanId(floorId, chunkIds.get(0))
                .orElseThrow(() -> notFound("SCAN_CHUNK_NOT_FOUND", "scan chunk not found"));
        floorScanRepository.deactivateForFloor(floorId);
        floorScanRepository.flush();
        target.setActive(true);
        target.setStatus("MERGED");
        floorScanRepository.saveAndFlush(target);
        return new MergedScanResponse(floorId, target.getScan().getScanId(), "MERGED");
    }

    @Override
    public MergedScanResponse mergeStatus(UUID floorId) {
        requireFloor(floorId);
        return activeScan(floorId)
                .map(scan -> new MergedScanResponse(floorId, scan.getScan().getScanId(), "MERGED"))
                .orElseGet(() -> new MergedScanResponse(floorId, null, "IDLE"));
    }

    @Override
    @Transactional
    public ProcessingStatusResponse process(UUID floorId) {
        requireFloor(floorId);
        FloorScanEntity active = activeScan(floorId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.CONFLICT, "ACTIVE_SCAN_NOT_FOUND", "floor has no active scan"));
        ScanIngestEntity scan = active.getScan();
        BuildJobEntity job = buildJobRepository.saveAndFlush(new BuildJobEntity(scan));
        scan.setBuildState(BuildState.pending);
        scan.setBuildJobId(job.getBuildJobId());
        scanIngestRepository.saveAndFlush(scan);
        return new ProcessingStatusResponse(floorId, scan.getScanId(), job.getBuildJobId(), "QUEUED", 0.0, null);
    }

    @Override
    public ProcessingStatusResponse processStatus(UUID floorId) {
        requireFloor(floorId);
        Optional<FloorScanEntity> active = activeScan(floorId);
        if (active.isEmpty()) {
            return new ProcessingStatusResponse(floorId, null, null, "IDLE", null, null);
        }
        UUID scanId = active.get().getScan().getScanId();
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(job -> new ProcessingStatusResponse(
                        floorId,
                        scanId,
                        job.getBuildJobId(),
                        publicBuildState(job.getState()),
                        job.getProgress(),
                        firstNonBlank(
                                job.getFailureReason() == null ? null : job.getFailureReason().name(),
                                job.getFailureDetail()
                        )
                ))
                .orElseGet(() -> new ProcessingStatusResponse(
                        floorId,
                        scanId,
                        null,
                        publicBuildState(active.get().getScan().getBuildState()),
                        null,
                        null
                ));
    }

    @Override
    public PathfindingResponse pathfinding(UUID buildingId, PathfindingRequest request) {
        requireBuilding(buildingId);
        RoutePosition start = new RoutePosition(
                request.startX(),
                request.startY(),
                request.startZ(),
                request.startFloorLevel()
        );
        PoiRouteTarget target = findPoiTarget(buildingId, request.destinationName());
        if (target == null) {
            return new PathfindingResponse(
                    buildingId,
                    0.0,
                    0,
                    List.of(new PathStepResponse(1, request.startFloorLevel(), start, "Start", null)),
                    List.of(),
                    metadata("destinationName", request.destinationName(), "destinationFound", false)
            );
        }

        List<PathStepResponse> steps = new ArrayList<>();
        steps.add(new PathStepResponse(1, request.startFloorLevel(), start, "Start", null));
        if (request.startScanId() != null && target.routeNodeId() != null) {
            UUID nearestNode = nearestNode(request.startScanId(), request.startX(), request.startY(), request.startZ());
            if (nearestNode != null) {
                RouteResult route = routeBetween(request.startScanId(), nearestNode, target.routeNodeId());
                if (!route.nodes().isEmpty()) {
                    int stepNumber = 2;
                    for (MapNodeEntity node : route.nodes()) {
                        steps.add(new PathStepResponse(
                                stepNumber++,
                                target.floorLevel(),
                                new RoutePosition(x(node.getGeom()), y(node.getGeom()), z(node.getGeom()), target.floorLevel()),
                                node.getLabel() == null ? "Continue" : node.getLabel(),
                                node.getNodeId()
                        ));
                    }
                    return new PathfindingResponse(
                            buildingId,
                            route.totalDistance(),
                            estimateSeconds(route.totalDistance()),
                            steps,
                            List.of(),
                            metadata("destinationName", request.destinationName(), "destinationFound", true)
                    );
                }
            }
        }

        RoutePosition destination = new RoutePosition(target.x(), target.y(), target.z(), target.floorLevel());
        steps.add(new PathStepResponse(2, target.floorLevel(), destination, "Arrive", target.routeNodeId()));
        double distance = distance(request.startX(), request.startY(), request.startZ(), target.x(), target.y(), target.z());
        return new PathfindingResponse(
                buildingId,
                distance,
                estimateSeconds(distance),
                steps,
                List.of(),
                metadata("destinationName", request.destinationName(), "destinationFound", true)
        );
    }

    @Override
    public Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        requireFloor(floorId);
        Optional<FloorScanEntity> active = activeScan(floorId);
        if (active.isEmpty()) {
            return metadata("floorId", floorId, "from", fromNode, "to", toNode, "nodes", List.of(), "edges", List.of());
        }
        UUID scanId = active.get().getScan().getScanId();
        RouteResult route = routeBetween(scanId, fromNode, toNode);
        return metadata(
                "floorId", floorId,
                "scanId", scanId,
                "from", fromNode,
                "to", toNode,
                "totalDistance", route.totalDistance(),
                "nodes", route.nodes().stream().map(this::nodeMap).toList(),
                "edges", route.edges().stream().map(this::edgeMap).toList()
        );
    }

    @Override
    public List<POIResponse> listPois(UUID buildingId) {
        requireBuilding(buildingId);
        return poiCanonicalRepository.findByBuilding_BuildingIdOrderByNameAscLabelAsc(buildingId).stream()
                .map(this::toPoiResponse)
                .toList();
    }

    @Override
    public List<POIResponse> searchPois(UUID buildingId, String query) {
        requireBuilding(buildingId);
        if (query == null || query.isBlank()) {
            return listPois(buildingId);
        }
        return poiCanonicalRepository.search(buildingId, "%" + query.toLowerCase() + "%").stream()
                .map(this::toPoiResponse)
                .toList();
    }

    private BuildingEntity requireBuilding(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> notFound("BUILDING_NOT_FOUND", "building not found"));
    }

    private FloorEntity requireFloor(UUID floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> notFound("FLOOR_NOT_FOUND", "floor not found"));
    }

    private Optional<FloorScanEntity> activeScan(UUID floorId) {
        return floorScanRepository.findFirstByFloor_FloorIdAndActiveTrueOrderByCreatedAtDesc(floorId);
    }

    private List<MapNodeEntity> nodes(UUID scanId) {
        return mapNodeRepository.findByScanIdAndStaleFalseOrderByNodeId(scanId);
    }

    private List<MapEdgeEntity> edges(UUID scanId) {
        return mapEdgeRepository.findByScanIdAndStaleFalseOrderByEdgeId(scanId);
    }

    private UUID latestBuildJobId(UUID scanId) {
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(BuildJobEntity::getBuildJobId)
                .orElse(null);
    }

    private UUID nearestNode(UUID scanId, double targetX, double targetY, double targetZ) {
        return nodes(scanId).stream()
                .min(Comparator.comparingDouble(node -> distance(
                        x(node.getGeom()),
                        y(node.getGeom()),
                        z(node.getGeom()),
                        targetX,
                        targetY,
                        targetZ
                )))
                .map(MapNodeEntity::getNodeId)
                .orElse(null);
    }

    private RouteResult routeBetween(UUID scanId, UUID fromNode, UUID toNode) {
        List<MapNodeEntity> nodes = nodes(scanId);
        Map<UUID, MapNodeEntity> nodeById = new HashMap<>();
        for (MapNodeEntity node : nodes) {
            nodeById.put(node.getNodeId(), node);
        }
        if (!nodeById.containsKey(fromNode) || !nodeById.containsKey(toNode)) {
            throw notFound("ROUTE_NODE_NOT_FOUND", "route node not found");
        }

        List<MapEdgeEntity> edges = edges(scanId);
        Map<UUID, List<RouteEdge>> adjacency = new HashMap<>();
        for (MapEdgeEntity edge : edges) {
            RouteEdge forward = new RouteEdge(edge.getEdgeId(), edge.getFromNodeId(), edge.getToNodeId(), edge.getLengthM(), edge.getEdgeType().name());
            RouteEdge reverse = forward.reverse();
            adjacency.computeIfAbsent(forward.fromId(), ignored -> new ArrayList<>()).add(forward);
            adjacency.computeIfAbsent(reverse.fromId(), ignored -> new ArrayList<>()).add(reverse);
        }

        Map<UUID, Double> distanceByNode = new HashMap<>();
        Map<UUID, UUID> previousNode = new HashMap<>();
        Map<UUID, RouteEdge> previousEdge = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        distanceByNode.put(fromNode, 0.0);
        queue.add(new NodeDistance(fromNode, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (current.distance() > distanceByNode.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.nodeId().equals(toNode)) {
                break;
            }
            for (RouteEdge edge : adjacency.getOrDefault(current.nodeId(), List.of())) {
                double nextDistance = current.distance() + edge.lengthM();
                if (nextDistance < distanceByNode.getOrDefault(edge.toId(), Double.POSITIVE_INFINITY)) {
                    distanceByNode.put(edge.toId(), nextDistance);
                    previousNode.put(edge.toId(), current.nodeId());
                    previousEdge.put(edge.toId(), edge);
                    queue.add(new NodeDistance(edge.toId(), nextDistance));
                }
            }
        }

        if (!distanceByNode.containsKey(toNode)) {
            return new RouteResult(List.of(), List.of(), 0.0);
        }

        ArrayDeque<MapNodeEntity> routeNodes = new ArrayDeque<>();
        ArrayDeque<RouteEdge> routeEdges = new ArrayDeque<>();
        UUID cursor = toNode;
        routeNodes.addFirst(nodeById.get(cursor));
        while (!cursor.equals(fromNode)) {
            RouteEdge edge = previousEdge.get(cursor);
            if (edge == null) {
                return new RouteResult(List.of(), List.of(), 0.0);
            }
            routeEdges.addFirst(edge);
            cursor = previousNode.get(cursor);
            routeNodes.addFirst(nodeById.get(cursor));
        }
        return new RouteResult(List.copyOf(routeNodes), List.copyOf(routeEdges), distanceByNode.get(toNode));
    }

    private PoiRouteTarget findPoiTarget(UUID buildingId, String destinationName) {
        if (destinationName == null || destinationName.isBlank()) {
            return null;
        }
        return poiCanonicalRepository.search(buildingId, "%" + destinationName.toLowerCase() + "%").stream()
                .map(poi -> {
                    Point point = firstPoint(poi);
                    return new PoiRouteTarget(
                            poi.getRouteNodeId(),
                            point == null ? 0.0 : x(point),
                            point == null ? 0.0 : y(point),
                            point == null ? 0.0 : z(point),
                            poi.getFloor() == null ? null : poi.getFloor().getLevel()
                    );
                })
                .findFirst()
                .orElse(null);
    }

    private BuildingResponse toBuildingResponse(BuildingEntity building) {
        return new BuildingResponse(
                building.getBuildingId(),
                building.getName(),
                building.getDescription(),
                building.getLatitude(),
                building.getLongitude(),
                BuildingStatus.valueOf(building.getStatus()),
                building.getCreatedAt(),
                building.getUpdatedAt()
        );
    }

    private FloorResponse toFloorResponse(FloorEntity floor) {
        Optional<FloorScanEntity> active = activeScan(floor.getFloorId());
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        return new FloorResponse(
                floor.getFloorId(),
                floor.getBuilding().getBuildingId(),
                floor.getName(),
                floor.getLevel(),
                floor.getHeight(),
                scanId != null && mapNodeRepository.existsByScanIdAndStaleFalse(scanId),
                false,
                scanId,
                floor.getCreatedAt(),
                floor.getUpdatedAt()
        );
    }

    private ScanChunkResponse toScanChunkResponse(FloorScanEntity scan) {
        return new ScanChunkResponse(
                scan.getFloorScanId(),
                scan.getFloor().getFloorId(),
                scan.getScan().getScanId(),
                scan.getFileName(),
                scan.getFileSize(),
                scan.getStatus(),
                scan.isActive(),
                scan.getUploadOrder(),
                scan.getCreatedAt()
        );
    }

    private POIResponse toPoiResponse(PoiCanonicalEntity poi) {
        Point point = firstPoint(poi);
        Map<String, Double> displayPoint = point == null
                ? null
                : Map.of("x", x(point), "y", y(point), "z", z(point));
        return new POIResponse(
                poi.getCanonicalId(),
                poi.getBuilding() == null ? null : poi.getBuilding().getBuildingId(),
                poi.getFloor() == null ? null : poi.getFloor().getFloorId(),
                poi.getName(),
                poi.getLabel(),
                poi.getCategory(),
                poi.getRouteNodeId(),
                displayPoint,
                poi.isNeedsReview(),
                poi.getLlmConfidence()
        );
    }

    private FloorMapNode floorMapNode(MapNodeEntity node) {
        return new FloorMapNode(
                node.getNodeId(),
                node.getNodeType().name(),
                x(node.getGeom()),
                y(node.getGeom()),
                z(node.getGeom()),
                node.getLabel(),
                null
        );
    }

    private FloorMapEdge floorMapEdge(MapEdgeEntity edge) {
        return new FloorMapEdge(
                edge.getEdgeId(),
                edge.getFromNodeId(),
                edge.getToNodeId(),
                edge.getLengthM(),
                edge.getEdgeType().name()
        );
    }

    private Map<String, Object> nodeMap(MapNodeEntity node) {
        return metadata(
                "id", node.getNodeId(),
                "type", node.getNodeType().name(),
                "x", x(node.getGeom()),
                "y", y(node.getGeom()),
                "z", z(node.getGeom()),
                "label", node.getLabel()
        );
    }

    private Map<String, Object> edgeMap(MapEdgeEntity edge) {
        return metadata(
                "id", edge.getEdgeId(),
                "fromId", edge.getFromNodeId(),
                "toId", edge.getToNodeId(),
                "lengthM", edge.getLengthM(),
                "type", edge.getEdgeType().name()
        );
    }

    private Map<String, Object> edgeMap(RouteEdge edge) {
        return metadata(
                "id", edge.id(),
                "fromId", edge.fromId(),
                "toId", edge.toId(),
                "lengthM", edge.lengthM(),
                "type", edge.type()
        );
    }

    private FloorMapBounds floorMapBounds(List<MapNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return new FloorMapBounds(0, 0, 0, 0, 0, 0);
        }
        Bounds bounds = computeBounds(nodes);
        return new FloorMapBounds(
                bounds.minX(),
                bounds.minY(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxX() - bounds.minX(),
                bounds.maxY() - bounds.minY()
        );
    }

    private Map<String, Double> pathBounds(List<MapNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        Bounds bounds = computeBounds(nodes);
        return Map.of(
                "minX", bounds.minX(),
                "minY", bounds.minY(),
                "maxX", bounds.maxX(),
                "maxY", bounds.maxY(),
                "widthM", bounds.maxX() - bounds.minX(),
                "heightM", bounds.maxY() - bounds.minY()
        );
    }

    private Bounds computeBounds(List<MapNodeEntity> nodes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MapNodeEntity node : nodes) {
            minX = Math.min(minX, x(node.getGeom()));
            minY = Math.min(minY, y(node.getGeom()));
            maxX = Math.max(maxX, x(node.getGeom()));
            maxY = Math.max(maxY, y(node.getGeom()));
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private StoredUpload storeUpload(MultipartFile upload, Path destination) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_UNAVAILABLE", e.getMessage());
        }
        long size = 0;
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = upload.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    size += read;
                }
            }
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED", e.getMessage());
        }
        return new StoredUpload(destination, HexFormat.of().formatHex(digest.digest()), size);
    }

    private Map<String, Object> deviceInfoMap(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(deviceInfo, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of("raw", deviceInfo);
        }
    }

    private Point firstPoint(PoiCanonicalEntity poi) {
        return poi.getDisplayPoint() != null ? poi.getDisplayPoint() : poi.getWorldPose();
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.bin";
        }
        return Path.of(originalFilename.replace('\\', '/')).getFileName().toString();
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

    private ClientApiException notFound(String code, String message) {
        return new ClientApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private double x(Point point) {
        return point.getX();
    }

    private double y(Point point) {
        return point.getY();
    }

    private double z(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return Double.isNaN(coordinate.getZ()) ? 0.0 : coordinate.getZ();
    }

    private String publicBuildState(BuildState state) {
        if (state == null || state == BuildState.not_started) {
            return "IDLE";
        }
        if (state == BuildState.pending) {
            return "QUEUED";
        }
        return state.name().toUpperCase();
    }

    private int estimateSeconds(double distanceM) {
        return (int) Math.ceil(distanceM / 1.2);
    }

    private double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private String etagFor(UUID floorId, UUID scanId, UUID buildJobId, int nodeCount, int edgeCount) {
        return Integer.toHexString(Objects.hash(floorId, scanId, buildJobId, nodeCount, edgeCount));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup after a DB failure.
        }
    }

    private record StoredUpload(Path path, String sha256, long size) {
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }

    private record NodeDistance(UUID nodeId, double distance) {
    }

    private record RouteEdge(UUID id, UUID fromId, UUID toId, double lengthM, String type) {
        RouteEdge reverse() {
            return new RouteEdge(id, toId, fromId, lengthM, type);
        }
    }

    private record RouteResult(List<MapNodeEntity> nodes, List<RouteEdge> edges, double totalDistance) {
    }

    private record PoiRouteTarget(UUID routeNodeId, double x, double y, double z, Integer floorLevel) {
    }
}
