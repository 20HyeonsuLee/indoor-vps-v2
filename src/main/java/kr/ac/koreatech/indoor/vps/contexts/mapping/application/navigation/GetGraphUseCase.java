package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GetGraphUseCase {
    private final FloorQueryService floorQuery;
    private final GraphQueryFacade graphQueryFacade;

    public GetGraphUseCase(
            FloorQueryService floorQuery,
            GraphQueryFacade graphQueryFacade
    ) {
        this.floorQuery = floorQuery;
        this.graphQueryFacade = graphQueryFacade;
    }

    public FloorPathResult getFloorPath(UUID floorId) {
        floorQuery.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorQuery.activeScan(floorId);
        if (active.isEmpty()) {
            return FloorPathResult.empty(floorId);
        }
        UUID scanId = active.get().getScan().getScanId();
        List<RouteNode> nodes = graphQueryFacade.routeNodes(scanId);
        List<RouteEdge> edges = graphQueryFacade.routeEdges(scanId);
        UUID buildJobId = graphQueryFacade.latestBuildJobId(scanId).orElse(null);
        return new FloorPathResult(floorId, scanId, buildJobId, nodes, edges);
    }

    public record FloorPathResult(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            List<RouteNode> nodes,
            List<RouteEdge> edges
    ) {
        static FloorPathResult empty(UUID floorId) {
            return new FloorPathResult(floorId, null, null, List.of(), List.of());
        }
    }
}
