package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteEdge;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.navigation.RouteNode;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapEdgeRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MapNode/MapEdge 조회 + RouteNode/RouteEdge 변환을 캡슐화.
 * PlanRouteUseCase/GetGraphUseCase/GetFloorMapUseCase의 UseCase→UseCase 의존과
 * instance_vars 과다를 동시에 해결.
 */
@Component
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GraphQueryFacade {

    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;
    private final BuildJobRepository buildJobRepository;
    private final RouteGraphMapper graphMapper;

    public GraphQueryFacade(
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository,
            BuildJobRepository buildJobRepository,
            RouteGraphMapper graphMapper
    ) {
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
        this.buildJobRepository = buildJobRepository;
        this.graphMapper = graphMapper;
    }

    public List<RouteNode> routeNodes(UUID scanId) {
        return graphMapper.toRouteNodes(mapNodeRepository.findByScanIdAndStaleFalseOrderByNodeId(scanId));
    }

    public List<RouteEdge> routeEdges(UUID scanId) {
        return graphMapper.toRouteEdges(mapEdgeRepository.findByScanIdAndStaleFalseOrderByEdgeId(scanId));
    }

    public List<MapNodeEntity> nodeEntities(UUID scanId) {
        return mapNodeRepository.findByScanIdAndStaleFalseOrderByNodeId(scanId);
    }

    public List<MapEdgeEntity> edgeEntities(UUID scanId) {
        return mapEdgeRepository.findByScanIdAndStaleFalseOrderByEdgeId(scanId);
    }

    public Optional<UUID> latestBuildJobId(UUID scanId) {
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(BuildJobEntity::getBuildJobId);
    }

    public Optional<UUID> findNodeArea(UUID nodeId) {
        return mapNodeRepository.findById(nodeId)
                .map(MapNodeEntity::getAreaId);
    }
}
