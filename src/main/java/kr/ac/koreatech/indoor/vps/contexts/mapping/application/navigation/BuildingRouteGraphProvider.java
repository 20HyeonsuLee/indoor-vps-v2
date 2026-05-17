package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * building 단위 composite route graph (cross-area routing 골격).
 * build_job 완료 시 해당 building graph를 invalidate.
 * 현재: in-memory cache, single-invalidate-on-build.
 */
@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildingRouteGraphProvider {

    private final FloorScanRepository floorScanRepository;
    private final GraphQueryFacade graphQueryFacade;
    private final Map<UUID, CompositeGraph> cache = new ConcurrentHashMap<>();

    public BuildingRouteGraphProvider(FloorScanRepository floorScanRepository, GraphQueryFacade graphQueryFacade) {
        this.floorScanRepository = floorScanRepository;
        this.graphQueryFacade = graphQueryFacade;
    }

    @Transactional(readOnly = true)
    public CompositeGraph getOrBuild(UUID buildingId) {
        return cache.computeIfAbsent(buildingId, this::buildGraph);
    }

    public void invalidate(UUID buildingId) {
        cache.remove(buildingId);
    }

    private CompositeGraph buildGraph(UUID buildingId) {
        List<FloorScanEntity> activeScans = floorScanRepository.findActiveForBuilding(buildingId);
        List<RouteNode> allNodes = new ArrayList<>();
        List<RouteEdge> allEdges = new ArrayList<>();
        for (FloorScanEntity fs : activeScans) {
            UUID scanId = fs.getScan().getScanId();
            allNodes.addAll(graphQueryFacade.routeNodes(scanId));
            allEdges.addAll(graphQueryFacade.routeEdges(scanId));
        }
        return new CompositeGraph(allNodes, allEdges);
    }

    public record CompositeGraph(List<RouteNode> nodes, List<RouteEdge> edges) {
        public boolean isCrossArea(UUID fromNode, UUID toNode) {
            UUID fromArea = nodes.stream()
                    .filter(n -> n.id().equals(fromNode))
                    .findFirst()
                    .map(RouteNode::areaId)
                    .orElse(null);
            UUID toArea = nodes.stream()
                    .filter(n -> n.id().equals(toNode))
                    .findFirst()
                    .map(RouteNode::areaId)
                    .orElse(null);
            if (fromArea == null || toArea == null) {
                return false;
            }
            return !fromArea.equals(toArea);
        }
    }
}
