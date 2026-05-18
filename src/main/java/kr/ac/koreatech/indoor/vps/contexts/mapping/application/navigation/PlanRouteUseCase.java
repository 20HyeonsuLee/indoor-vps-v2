package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.BuildingRouteGraphProvider.CompositeGraph;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult.PathStep;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult.RoutePosition;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PoiRouteTargetResolver.PoiRouteTarget;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
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
    private final NavigationQueryContext queryContext;
    private final NavigationGraphService graphService;
    private final GraphQueryFacade graphQueryFacade;
    private final BuildingRouteGraphProvider buildingRouteGraphProvider;

    public PlanRouteUseCase(
            NavigationQueryContext queryContext,
            NavigationGraphService graphService,
            GraphQueryFacade graphQueryFacade,
            BuildingRouteGraphProvider buildingRouteGraphProvider
    ) {
        this.queryContext = queryContext;
        this.graphService = graphService;
        this.graphQueryFacade = graphQueryFacade;
        this.buildingRouteGraphProvider = buildingRouteGraphProvider;
    }

    public PathfindingResult pathfinding(UUID buildingId, PathfindingCommand command) {
        queryContext.requireBuilding(buildingId);
        RoutePosition start = new RoutePosition(
                command.startX(),
                command.startY(),
                command.startZ(),
                command.startFloorLevel()
        );
        Optional<PoiRouteTarget> targetOpt = queryContext.findTarget(buildingId, command.destinationName());
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
        if (target.routeNodeId() != null) {
            CompositeGraph graph = buildingRouteGraphProvider.build(buildingId);
            List<RouteNode> nearestCandidates = command.startAreaId() == null
                    ? graph.nodes()
                    : graph.nodes().stream()
                            .filter(n -> command.startAreaId().equals(n.areaId()))
                            .toList();
            if (nearestCandidates.isEmpty()) {
                nearestCandidates = graph.nodes();
            }
            UUID nearestNode = graphService.nearestNode(
                    nearestCandidates,
                    new Point3(command.startX(), command.startY(), command.startZ())
            );
            if (nearestNode != null) {
                RouteResult route = graphService.routeBetween(graph.nodes(), graph.edges(), nearestNode, target.routeNodeId());
                if (!route.nodes().isEmpty()) {
                    int stepNumber = 2;
                    for (RouteNode node : route.nodes()) {
                        Integer nodeLevel = graph.floorLevelOf(node.id());
                        int stepLevel = nodeLevel != null ? nodeLevel : target.floorLevel();
                        steps.add(new PathStep(
                                stepNumber++,
                                stepLevel,
                                new RoutePosition(
                                        node.position().x(),
                                        node.position().y(),
                                        node.position().z(),
                                        stepLevel
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
                new Point3(command.startX(), command.startY(), command.startZ()),
                new Point3(target.x(), target.y(), target.z())
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

    public FloorRouteResult floorRoute(FloorRouteCommand command) {
        queryContext.requireFloor(command.floorId());
        Optional<FloorScanEntity> active = queryContext.activeScanForArea(command.floorId(), command.areaId());
        if (active.isEmpty()) {
            return new FloorRouteResult(command.floorId(), null, command.fromNode(), command.toNode(), 0.0, List.of(), List.of());
        }
        UUID scanId = active.get().getScan().getScanId();
        List<RouteNode> routeNodes = graphQueryFacade.routeNodes(scanId);
        RouteResult route = graphService.routeBetween(
                routeNodes,
                graphQueryFacade.routeEdges(scanId),
                command.fromNode(),
                command.toNode()
        );
        return new FloorRouteResult(command.floorId(), scanId, command.fromNode(), command.toNode(),
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
