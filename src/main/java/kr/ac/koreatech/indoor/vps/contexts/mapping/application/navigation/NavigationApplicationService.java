package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;
import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.NavigationDtos.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorApplicationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PoiRouteTargetResolver.PoiRouteTarget;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NavigationGraphService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class NavigationApplicationService {
    private final BuildingApplicationService buildingService;
    private final FloorApplicationService floorService;
    private final BuildJobRepository buildJobRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final NavigationGraphService graphService;
    private final RouteGraphMapper graphMapper;
    private final NavigationResponseMapper responseMapper;
    private final PoiRouteTargetResolver targetResolver;

    public NavigationApplicationService(
            BuildingApplicationService buildingService,
            FloorApplicationService floorService,
            BuildJobRepository buildJobRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            NavigationGraphService graphService,
            RouteGraphMapper graphMapper,
            NavigationResponseMapper responseMapper,
            PoiRouteTargetResolver targetResolver
    ) {
        this.buildingService = buildingService;
        this.floorService = floorService;
        this.buildJobRepository = buildJobRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.graphService = graphService;
        this.graphMapper = graphMapper;
        this.responseMapper = responseMapper;
        this.targetResolver = targetResolver;
    }

    public FloorPathResponse getFloorPath(UUID floorId) {
        floorService.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorService.activeScan(floorId);
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
                nodes.stream().map(responseMapper::nodeMap).toList(),
                edges.stream().map(responseMapper::edgeMap).toList(),
                responseMapper.pathBounds(nodes).orElse(null)
        );
    }

    public FloorMapResponse getFloorMap(UUID floorId) {
        FloorEntity floor = floorService.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorService.activeScan(floorId);
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
                responseMapper.floorMapBounds(nodes),
                Map.of("type", "FeatureCollection", "features", List.of()),
                nodes.stream().map(responseMapper::floorMapNode).toList(),
                edges.stream().map(responseMapper::floorMapEdge).toList(),
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
        Optional<PoiRouteTarget> targetOpt = targetResolver.find(buildingId, request.destinationName());
        if (targetOpt.isEmpty()) {
            return new PathfindingResponse(
                    buildingId,
                    0.0,
                    0,
                    List.of(new PathStepResponse(1, request.startFloorLevel(), start, "Start", null)),
                    List.of(),
                    responseMapper.metadata("destinationName", request.destinationName(), "destinationFound", false)
            );
        }
        PoiRouteTarget target = targetOpt.get();

        List<PathStepResponse> steps = new ArrayList<>();
        steps.add(new PathStepResponse(1, request.startFloorLevel(), start, "Start", null));
        if (request.startScanId() != null && target.routeNodeId() != null) {
            List<MapNodeEntity> nodes = nodes(request.startScanId());
            List<MapEdgeEntity> edges = edges(request.startScanId());
            List<RouteNode> routeNodes = graphMapper.toRouteNodes(nodes);
            List<RouteEdge> routeEdges = graphMapper.toRouteEdges(edges);
            UUID nearestNode = graphService.nearestNode(
                    routeNodes,
                    new Point3(request.startX(), request.startY(), request.startZ())
            );
            if (nearestNode != null) {
                RouteResult route = graphService.routeBetween(routeNodes, routeEdges, nearestNode, target.routeNodeId());
                if (!route.nodes().isEmpty()) {
                    int stepNumber = 2;
                    for (RouteNode node : route.nodes()) {
                        steps.add(new PathStepResponse(
                                stepNumber++,
                                target.floorLevel(),
                                new RoutePosition(
                                        node.position().x(),
                                        node.position().y(),
                                        node.position().z(),
                                        target.floorLevel()
                                ),
                                node.label() == null ? "Continue" : node.label(),
                                node.id()
                        ));
                    }
                    return new PathfindingResponse(
                            buildingId,
                            route.totalDistance(),
                            graphService.estimateWalkingSeconds(route.totalDistance()),
                            steps,
                            List.of(),
                            responseMapper.metadata("destinationName", request.destinationName(), "destinationFound", true)
                    );
                }
            }
        }

        RoutePosition destination = new RoutePosition(target.x(), target.y(), target.z(), target.floorLevel());
        steps.add(new PathStepResponse(2, target.floorLevel(), destination, "Arrive", target.routeNodeId()));
        double distance = NavigationGeometry.distance(
                request.startX(),
                request.startY(),
                request.startZ(),
                target.x(),
                target.y(),
                target.z()
        );
        return new PathfindingResponse(
                buildingId,
                distance,
                graphService.estimateWalkingSeconds(distance),
                steps,
                List.of(),
                responseMapper.metadata("destinationName", request.destinationName(), "destinationFound", true)
        );
    }

    public Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        floorService.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorService.activeScan(floorId);
        if (active.isEmpty()) {
            return responseMapper.metadata("floorId", floorId, "from", fromNode, "to", toNode, "nodes", List.of(), "edges", List.of());
        }
        UUID scanId = active.get().getScan().getScanId();
        List<MapNodeEntity> nodeEntities = nodes(scanId);
        RouteResult route = graphService.routeBetween(
                graphMapper.toRouteNodes(nodeEntities),
                graphMapper.toRouteEdges(edges(scanId)),
                fromNode,
                toNode
        );
        return responseMapper.metadata(
                "floorId", floorId,
                "scanId", scanId,
                "from", fromNode,
                "to", toNode,
                "totalDistance", route.totalDistance(),
                "nodes", route.nodes().stream().map(responseMapper::nodeMap).toList(),
                "edges", route.edges().stream().map(responseMapper::edgeMap).toList()
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

    private String etagFor(UUID floorId, UUID scanId, UUID buildJobId, int nodeCount, int edgeCount) {
        return Integer.toHexString(Objects.hash(floorId, scanId, buildJobId, nodeCount, edgeCount));
    }

}
