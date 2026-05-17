package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GetFloorMapUseCase {
    private final FloorUseCase floorUseCase;
    private final GetGraphUseCase getGraphUseCase;

    public GetFloorMapUseCase(FloorUseCase floorUseCase, GetGraphUseCase getGraphUseCase) {
        this.floorUseCase = floorUseCase;
        this.getGraphUseCase = getGraphUseCase;
    }

    public FloorMapResult getFloorMap(UUID floorId) {
        FloorEntity floor = floorUseCase.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorUseCase.activeScan(floorId);
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        UUID buildJobId = scanId == null ? null : getGraphUseCase.latestBuildJobId(scanId);
        List<MapNodeEntity> nodes = scanId == null ? List.of() : getGraphUseCase.nodes(scanId);
        List<MapEdgeEntity> edges = scanId == null ? List.of() : getGraphUseCase.edges(scanId);
        String etag = etagFor(floor.getFloorId(), scanId, buildJobId, nodes.size(), edges.size());
        return new FloorMapResult(floor, scanId, buildJobId, nodes, edges, etag);
    }

    private String etagFor(UUID floorId, UUID scanId, UUID buildJobId, int nodeCount, int edgeCount) {
        return Integer.toHexString(Objects.hash(floorId, scanId, buildJobId, nodeCount, edgeCount));
    }

    public record FloorMapResult(
            FloorEntity floor,
            UUID scanId,
            UUID buildJobId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            String etag
    ) {
    }
}
