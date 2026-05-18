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
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NavigationGraphService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.NodeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.Point3;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteResult;
import java.util.HashMap;
import java.util.Map;
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
        Optional<PoiRouteTarget> targetOpt = resolveTarget(buildingId, command);
        if (targetOpt.isEmpty()) {
            return new PathfindingResult(
                    buildingId,
                    0.0,
                    0,
                    List.of(new PathStep(1, command.startFloorLevel(), start, "Start", null)),
                    List.of(),
                    metadataOf(
                            "destinationId", command.destinationId(),
                            "destinationName", command.destinationName(),
                            "destinationFound", false
                    )
            );
        }
        PoiRouteTarget target = targetOpt.get();

        List<PathStep> steps = new ArrayList<>();
        steps.add(new PathStep(1, command.startFloorLevel(), start, "Start", null));
        if (target.routeNodeId() != null) {
            CompositeGraph graph = buildingRouteGraphProvider.build(buildingId);
            Point3 startPos = new Point3(command.startX(), command.startY(), command.startZ());
            List<RouteNode> mutableNodes = new ArrayList<>(graph.nodes());
            List<RouteEdge> mutableEdges = new ArrayList<>(graph.edges());
            UUID startNodeId = injectStartProjection(startPos, command.startAreaId(),
                    mutableNodes, mutableEdges);
            if (startNodeId != null) {
                RouteResult route = graphService.routeBetween(mutableNodes, mutableEdges,
                        startNodeId, target.routeNodeId());
                if (!route.nodes().isEmpty()) {
                    Map<UUID, RouteNode> byId = new HashMap<>();
                    for (RouteNode n : mutableNodes) {
                        byId.put(n.id(), n);
                    }
                    int stepNumber = 2;
                    // Skip the very first route node — that is the synthetic start which
                    // step 1 ("Start") already represents at the user's exact pose.
                    List<RouteNode> routeNodes = route.nodes();
                    for (int i = 1; i < routeNodes.size(); i++) {
                        RouteNode node = routeNodes.get(i);
                        Integer nodeLevel = floorLevelFor(node, graph);
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
                            metadataOf(
                                    "destinationId", command.destinationId(),
                                    "destinationName", command.destinationName(),
                                    "destinationFound", true
                            )
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
                metadataOf(
                        "destinationId", command.destinationId(),
                        "destinationName", command.destinationName(),
                        "destinationFound", true
                )
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

    private static final double START_SNAP_EPSILON = 0.05;
    private static final double START_VIRTUAL_FALLBACK_LEN = 0.001;

    /**
     * Inject the user's exact start pose as a virtual node, then perpendicular-foot
     * splice it onto the nearest sequential edge (within the same area if hinted).
     *
     * mutableNodes / mutableEdges are modified in place.
     * Returns the virtual start node id, or null when no sequential edge is reachable.
     */
    private UUID injectStartProjection(
            Point3 startPos,
            UUID startAreaId,
            List<RouteNode> mutableNodes,
            List<RouteEdge> mutableEdges
    ) {
        Map<UUID, RouteNode> byId = new HashMap<>();
        for (RouteNode n : mutableNodes) {
            byId.put(n.id(), n);
        }
        List<RouteEdge> sequentialCandidates = new ArrayList<>();
        for (RouteEdge e : mutableEdges) {
            if (e.type() != EdgeType.rtabmap_link) continue;
            RouteNode from = byId.get(e.fromId());
            RouteNode to = byId.get(e.toId());
            if (from == null || to == null) continue;
            if (startAreaId != null
                    && !startAreaId.equals(from.areaId())
                    && !startAreaId.equals(to.areaId())) {
                continue;
            }
            sequentialCandidates.add(e);
        }
        if (sequentialCandidates.isEmpty()) {
            // Fallback: try without area filter when nothing matched.
            for (RouteEdge e : mutableEdges) {
                if (e.type() == EdgeType.rtabmap_link
                        && byId.containsKey(e.fromId()) && byId.containsKey(e.toId())) {
                    sequentialCandidates.add(e);
                }
            }
        }
        if (sequentialCandidates.isEmpty()) {
            return null;
        }

        RouteEdge bestEdge = null;
        double bestDist = Double.POSITIVE_INFINITY;
        double bestT = 0.0;
        Point3 bestFoot = null;
        for (RouteEdge e : sequentialCandidates) {
            RouteNode from = byId.get(e.fromId());
            RouteNode to = byId.get(e.toId());
            Projection p = project(startPos, from.position(), to.position());
            if (p.distance < bestDist) {
                bestDist = p.distance;
                bestEdge = e;
                bestT = p.t;
                bestFoot = p.foot;
            }
        }
        if (bestEdge == null) {
            return null;
        }
        RouteNode fromN = byId.get(bestEdge.fromId());
        RouteNode toN = byId.get(bestEdge.toId());
        UUID anchorAreaId = startAreaId != null ? startAreaId : fromN.areaId();

        UUID startId = UUID.randomUUID();
        RouteNode startNode = new RouteNode(startId, NodeType.poi, startPos, "start", anchorAreaId);
        mutableNodes.add(startNode);

        if (bestT > START_SNAP_EPSILON && bestT < 1.0 - START_SNAP_EPSILON) {
            // Interior — split edge at foot, virtual junction in the middle.
            UUID footId = UUID.randomUUID();
            RouteNode footNode = new RouteNode(footId, NodeType.junction, bestFoot, null, anchorAreaId);
            mutableNodes.add(footNode);
            double total = bestEdge.lengthM();
            mutableEdges.remove(bestEdge);
            mutableEdges.add(new RouteEdge(UUID.randomUUID(),
                    bestEdge.fromId(), footId, Math.max(START_VIRTUAL_FALLBACK_LEN, bestT * total),
                    EdgeType.rtabmap_link));
            mutableEdges.add(new RouteEdge(UUID.randomUUID(),
                    footId, bestEdge.toId(), Math.max(START_VIRTUAL_FALLBACK_LEN, (1.0 - bestT) * total),
                    EdgeType.rtabmap_link));
            mutableEdges.add(new RouteEdge(UUID.randomUUID(),
                    startId, footId, Math.max(START_VIRTUAL_FALLBACK_LEN, bestDist),
                    EdgeType.poi_spur));
        } else {
            // Endpoint snap — spur directly to the nearer endpoint.
            UUID endpointId = bestT <= START_SNAP_EPSILON ? bestEdge.fromId() : bestEdge.toId();
            Point3 endpointPos = bestT <= START_SNAP_EPSILON ? fromN.position() : toN.position();
            mutableEdges.add(new RouteEdge(UUID.randomUUID(),
                    startId, endpointId, Math.max(START_VIRTUAL_FALLBACK_LEN, startPos.distanceTo(endpointPos)),
                    EdgeType.poi_spur));
        }
        return startId;
    }

    private Optional<PoiRouteTarget> resolveTarget(UUID buildingId, PathfindingCommand command) {
        if (command.destinationId() != null) {
            return queryContext.findTargetById(buildingId, command.destinationId());
        }
        return queryContext.findTargetByName(buildingId, command.destinationName());
    }

    private Integer floorLevelFor(RouteNode node, CompositeGraph graph) {
        return node.areaId() == null ? null : graph.areaToFloorLevel().get(node.areaId());
    }

    private record Projection(double t, Point3 foot, double distance) {
    }

    private Projection project(Point3 c, Point3 p, Point3 q) {
        double vx = q.x() - p.x();
        double vy = q.y() - p.y();
        double vz = q.z() - p.z();
        double wx = c.x() - p.x();
        double wy = c.y() - p.y();
        double wz = c.z() - p.z();
        double vv = vx * vx + vy * vy + vz * vz;
        double wv = wx * vx + wy * vy + wz * vz;
        double t = (vv == 0.0) ? 0.0 : Math.min(1.0, Math.max(0.0, wv / vv));
        Point3 foot = new Point3(p.x() + t * vx, p.y() + t * vy, p.z() + t * vz);
        double distance = c.distanceTo(foot);
        return new Projection(t, foot, distance);
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
