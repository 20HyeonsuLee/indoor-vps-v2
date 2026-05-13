package kr.ac.koreatech.indoor.vps.application.persistence;

import static kr.ac.koreatech.indoor.vps.api.dto.MapDtos.*;
import static kr.ac.koreatech.indoor.vps.api.dto.NavigationDtos.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.persistence.NavigationGraphService.RouteEdge;
import kr.ac.koreatech.indoor.vps.application.persistence.NavigationGraphService.RouteResult;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapNodeRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.PoiCanonicalRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class NavigationJpaService {
    private final BuildingJpaService buildingService;
    private final BuildJobRepository buildJobRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final PoiCanonicalRepository poiCanonicalRepository;
    private final NavigationGraphService graphService;

    public NavigationJpaService(
            BuildingJpaService buildingService,
            BuildJobRepository buildJobRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            PoiCanonicalRepository poiCanonicalRepository,
            NavigationGraphService graphService
    ) {
        this.buildingService = buildingService;
        this.buildJobRepository = buildJobRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.poiCanonicalRepository = poiCanonicalRepository;
        this.graphService = graphService;
    }

    public FloorPathResponse getFloorPath(UUID floorId) {
        buildingService.requireFloor(floorId);
        Optional<FloorScanEntity> active = buildingService.activeScan(floorId);
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

    public FloorMapResponse getFloorMap(UUID floorId) {
        FloorEntity floor = buildingService.requireFloor(floorId);
        Optional<FloorScanEntity> active = buildingService.activeScan(floorId);
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

    public PathfindingResponse pathfinding(UUID buildingId, PathfindingRequest request) {
        buildingService.requireBuilding(buildingId);
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
            List<MapNodeEntity> nodes = nodes(request.startScanId());
            List<MapEdgeEntity> edges = edges(request.startScanId());
            UUID nearestNode = graphService.nearestNode(nodes, request.startX(), request.startY(), request.startZ());
            if (nearestNode != null) {
                RouteResult route = graphService.routeBetween(nodes, edges, nearestNode, target.routeNodeId());
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

    public Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        buildingService.requireFloor(floorId);
        Optional<FloorScanEntity> active = buildingService.activeScan(floorId);
        if (active.isEmpty()) {
            return metadata("floorId", floorId, "from", fromNode, "to", toNode, "nodes", List.of(), "edges", List.of());
        }
        UUID scanId = active.get().getScan().getScanId();
        RouteResult route = graphService.routeBetween(nodes(scanId), edges(scanId), fromNode, toNode);
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

    private Point firstPoint(PoiCanonicalEntity poi) {
        return poi.getDisplayPoint() != null ? poi.getDisplayPoint() : poi.getWorldPose();
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

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
    }

    private record PoiRouteTarget(UUID routeNodeId, double x, double y, double z, Integer floorLevel) {
    }
}
