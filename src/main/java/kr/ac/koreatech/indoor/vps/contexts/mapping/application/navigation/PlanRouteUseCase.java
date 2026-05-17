package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult.PathStep;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult.RoutePosition;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PoiRouteTargetResolver.PoiRouteTarget;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NavigationGraphService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PlanRouteUseCase {
    private final BuildingQueryService buildingQuery;
    private final FloorQueryService floorQuery;
    private final NavigationGraphService graphService;
    private final RouteGraphMapper graphMapper;
    private final PoiRouteTargetResolver targetResolver;
    private final GetGraphUseCase getGraphUseCase;

    public PlanRouteUseCase(
            BuildingQueryService buildingQuery,
            FloorQueryService floorQuery,
            NavigationGraphService graphService,
            RouteGraphMapper graphMapper,
            PoiRouteTargetResolver targetResolver,
            GetGraphUseCase getGraphUseCase
    ) {
        this.buildingQuery = buildingQuery;
        this.floorQuery = floorQuery;
        this.graphService = graphService;
        this.graphMapper = graphMapper;
        this.targetResolver = targetResolver;
        this.getGraphUseCase = getGraphUseCase;
    }

    public PathfindingResult pathfinding(UUID buildingId, PathfindingCommand command) {
        buildingQuery.requireBuilding(buildingId);
        RoutePosition start = new RoutePosition(
                command.startX(),
                command.startY(),
                command.startZ(),
                command.startFloorLevel()
        );
        Optional<PoiRouteTarget> targetOpt = targetResolver.find(buildingId, command.destinationName());
        if (targetOpt.isEmpty()) {
            return new PathfindingResult(
                    buildingId,
                    0.0,
                    0,
                    List.of(new PathStep(1, command.startFloorLevel(), start, "Start", null)),
                    List.of(),
                    metadataOf("destinationName", command.destinationName(), "destinationFound", false)
            );
        }
        PoiRouteTarget target = targetOpt.get();

        List<PathStep> steps = new ArrayList<>();
        steps.add(new PathStep(1, command.startFloorLevel(), start, "Start", null));
        if (command.startScanId() != null && target.routeNodeId() != null) {
            List<MapNodeEntity> nodes = getGraphUseCase.nodes(command.startScanId());
            List<MapEdgeEntity> edges = getGraphUseCase.edges(command.startScanId());
            List<RouteNode> routeNodes = graphMapper.toRouteNodes(nodes);
            List<RouteEdge> routeEdges = graphMapper.toRouteEdges(edges);
            UUID nearestNode = graphService.nearestNode(
                    routeNodes,
                    new Point3(command.startX(), command.startY(), command.startZ())
            );
            if (nearestNode != null) {
                RouteResult route = graphService.routeBetween(routeNodes, routeEdges, nearestNode, target.routeNodeId());
                if (!route.nodes().isEmpty()) {
                    int stepNumber = 2;
                    for (RouteNode node : route.nodes()) {
                        steps.add(new PathStep(
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
                    return new PathfindingResult(
                            buildingId,
                            route.totalDistance(),
                            graphService.estimateWalkingSeconds(route.totalDistance()),
                            steps,
                            List.of(),
                            metadataOf("destinationName", command.destinationName(), "destinationFound", true)
                    );
                }
            }
        }

        RoutePosition destination = new RoutePosition(target.x(), target.y(), target.z(), target.floorLevel());
        steps.add(new PathStep(2, target.floorLevel(), destination, "Arrive", target.routeNodeId()));
        double distance = NavigationGeometry.distance(
                command.startX(), command.startY(), command.startZ(),
                target.x(), target.y(), target.z()
        );
        return new PathfindingResult(
                buildingId,
                distance,
                graphService.estimateWalkingSeconds(distance),
                steps,
                List.of(),
                metadataOf("destinationName", command.destinationName(), "destinationFound", true)
        );
    }

    public FloorRouteResult floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        floorQuery.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorQuery.activeScan(floorId);
        if (active.isEmpty()) {
            return new FloorRouteResult(floorId, null, fromNode, toNode, 0.0, List.of(), List.of());
        }
        UUID scanId = active.get().getScan().getScanId();
        List<MapNodeEntity> nodeEntities = getGraphUseCase.nodes(scanId);
        RouteResult route = graphService.routeBetween(
                graphMapper.toRouteNodes(nodeEntities),
                graphMapper.toRouteEdges(getGraphUseCase.edges(scanId)),
                fromNode,
                toNode
        );
        return new FloorRouteResult(floorId, scanId, fromNode, toNode,
                route.totalDistance(), route.nodes(), route.edges());
    }

    private java.util.Map<String, Object> metadataOf(Object... pairs) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    public record FloorRouteResult(
            UUID floorId,
            UUID scanId,
            UUID fromNode,
            UUID toNode,
            double totalDistance,
            List<RouteNode> nodes,
            List<RouteEdge> edges
    ) {
    }
}
