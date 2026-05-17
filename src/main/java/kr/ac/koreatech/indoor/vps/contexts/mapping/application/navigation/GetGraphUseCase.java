package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
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
public class GetGraphUseCase {
    private final FloorQueryService floorQuery;
    private final BuildJobRepository buildJobRepository;
    private final MapNodeRepository mapNodeRepository;
    private final MapEdgeRepository mapEdgeRepository;

    public GetGraphUseCase(
            FloorQueryService floorQuery,
            BuildJobRepository buildJobRepository,
            MapNodeRepository mapNodeRepository,
            MapEdgeRepository mapEdgeRepository
    ) {
        this.floorQuery = floorQuery;
        this.buildJobRepository = buildJobRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.mapEdgeRepository = mapEdgeRepository;
    }

    public FloorPathResult getFloorPath(UUID floorId) {
        floorQuery.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorQuery.activeScan(floorId);
        if (active.isEmpty()) {
            return FloorPathResult.empty(floorId);
        }
        UUID scanId = active.get().getScan().getScanId();
        List<MapNodeEntity> nodes = nodes(scanId);
        List<MapEdgeEntity> edges = edges(scanId);
        return new FloorPathResult(floorId, scanId, latestBuildJobId(scanId), nodes, edges);
    }

    public List<MapNodeEntity> nodes(UUID scanId) {
        return mapNodeRepository.findByScanIdAndStaleFalseOrderByNodeId(scanId);
    }

    public List<MapEdgeEntity> edges(UUID scanId) {
        return mapEdgeRepository.findByScanIdAndStaleFalseOrderByEdgeId(scanId);
    }

    public UUID latestBuildJobId(UUID scanId) {
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(BuildJobEntity::getBuildJobId)
                .orElse(null);
    }

    public record FloorPathResult(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges
    ) {
        static FloorPathResult empty(UUID floorId) {
            return new FloorPathResult(floorId, null, null, List.of(), List.of());
        }
    }
}
