package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.EdgeType;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * building 단위 composite route graph (cross-area / cross-floor routing).
 * 빌딩의 active scans 노드/엣지를 합치고, 같은 vertical_connector의 stop들 사이에
 * synthetic bridge edge를 추가해 multi-floor 경로 탐색을 가능하게 함.
 */
@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildingRouteGraphProvider {

    private static final double CONNECTOR_BRIDGE_LENGTH_M = 1.0;
    private static final double NON_PREFERRED_VERTICAL_COST_M = 30.0;

    private final FloorScanRepository floorScanRepository;
    private final VerticalConnectorStopRepository stopRepository;
    private final GraphQueryFacade graphQueryFacade;

    public BuildingRouteGraphProvider(
            FloorScanRepository floorScanRepository,
            VerticalConnectorStopRepository stopRepository,
            GraphQueryFacade graphQueryFacade
    ) {
        this.floorScanRepository = floorScanRepository;
        this.stopRepository = stopRepository;
        this.graphQueryFacade = graphQueryFacade;
    }

    @Transactional(readOnly = true)
    public CompositeGraph build(UUID buildingId) {
        return build(buildingId, null);
    }

    @Transactional(readOnly = true)
    public CompositeGraph build(UUID buildingId, VerticalPreference verticalPreference) {
        List<FloorScanEntity> activeScans = floorScanRepository.findActiveForBuilding(buildingId);
        List<RouteNode> allNodes = new ArrayList<>();
        List<RouteEdge> allEdges = new ArrayList<>();
        Map<UUID, Integer> areaToFloorLevel = new HashMap<>();
        for (FloorScanEntity fs : activeScans) {
            UUID scanId = fs.getScan().getScanId();
            allNodes.addAll(graphQueryFacade.routeNodes(scanId));
            allEdges.addAll(graphQueryFacade.routeEdges(scanId));
            areaToFloorLevel.put(fs.getArea().getAreaId(), fs.getArea().getFloor().getLevel());
        }
        addConnectorBridges(buildingId, verticalPreference, allNodes, allEdges);
        return new CompositeGraph(allNodes, allEdges, areaToFloorLevel);
    }

    private void addConnectorBridges(
            UUID buildingId,
            VerticalPreference verticalPreference,
            List<RouteNode> nodes,
            List<RouteEdge> edges
    ) {
        Set<UUID> nodeIds = new HashSet<>();
        for (RouteNode n : nodes) {
            nodeIds.add(n.id());
        }
        Map<UUID, List<VerticalConnectorStopEntity>> byConnector = new HashMap<>();
        for (VerticalConnectorStopEntity stop : stopRepository.findByConnector_Building_BuildingId(buildingId)) {
            byConnector.computeIfAbsent(stop.getConnector().getConnectorId(), k -> new ArrayList<>()).add(stop);
        }
        for (List<VerticalConnectorStopEntity> stops : byConnector.values()) {
            if (stops.size() < 2) {
                continue;
            }
            for (int i = 0; i < stops.size(); i++) {
                for (int j = i + 1; j < stops.size(); j++) {
                    UUID a = stops.get(i).getRouteNodeId();
                    UUID b = stops.get(j).getRouteNodeId();
                    if (a == null || b == null || !nodeIds.contains(a) || !nodeIds.contains(b)) {
                        continue;
                    }
                    String connectorType = stops.get(i).getConnector().getConnectorType();
                    String connectorKey = stops.get(i).getConnector().getConnectorKey();
                    edges.add(new RouteEdge(
                            UUID.randomUUID(),
                            a,
                            b,
                            CONNECTOR_BRIDGE_LENGTH_M,
                            EdgeType.vertical_connector,
                            verticalCost(connectorType, verticalPreference),
                            connectorType,
                            connectorKey
                    ));
                }
            }
        }
    }

    private double verticalCost(String connectorType, VerticalPreference verticalPreference) {
        if (verticalPreference == null) {
            return CONNECTOR_BRIDGE_LENGTH_M;
        }
        boolean preferred = switch (verticalPreference) {
            case ELEVATOR -> isElevator(connectorType);
            case STAIRS -> isStairsLike(connectorType);
        };
        return preferred ? CONNECTOR_BRIDGE_LENGTH_M : NON_PREFERRED_VERTICAL_COST_M;
    }

    private boolean isElevator(String connectorType) {
        if (connectorType == null) {
            return false;
        }
        String normalized = connectorType.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("elevator") || normalized.equals("lift");
    }

    private boolean isStairsLike(String connectorType) {
        if (connectorType == null) {
            return false;
        }
        String normalized = connectorType.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("stair")
                || normalized.equals("stairs")
                || normalized.equals("staircase")
                || normalized.equals("escalator");
    }

    public record CompositeGraph(
            List<RouteNode> nodes,
            List<RouteEdge> edges,
            Map<UUID, Integer> areaToFloorLevel
    ) {
        public Integer floorLevelOf(UUID nodeId) {
            for (RouteNode node : nodes) {
                if (node.id().equals(nodeId)) {
                    return areaToFloorLevel.get(node.areaId());
                }
            }
            return null;
        }
    }
}
