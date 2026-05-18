package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaPolygonEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaPolygonRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GetFloorMapUseCase {
    private final FloorQueryService floorQuery;
    private final GraphQueryFacade graphQueryFacade;
    private final FloorAreaPolygonRepository polygonRepository;

    public GetFloorMapUseCase(
            FloorQueryService floorQuery,
            GraphQueryFacade graphQueryFacade,
            FloorAreaPolygonRepository polygonRepository
    ) {
        this.floorQuery = floorQuery;
        this.graphQueryFacade = graphQueryFacade;
        this.polygonRepository = polygonRepository;
    }

    public FloorMapResult getFloorMap(UUID floorId, Optional<UUID> areaId) {
        FloorEntity floor = floorQuery.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorQuery.activeScanForArea(floorId, areaId);
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        UUID buildJobId = scanId == null ? null : graphQueryFacade.latestBuildJobId(scanId).orElse(null);
        List<MapNodeEntity> nodes = scanId == null ? List.of() : graphQueryFacade.nodeEntities(scanId);
        List<MapEdgeEntity> edges = scanId == null ? List.of() : graphQueryFacade.edgeEntities(scanId);
        List<FloorAreaPolygonEntity> polygons = polygons(scanId, areaId);
        String etag = etagFor(floor.getFloorId(), scanId, buildJobId, nodes.size(), edges.size(), polygons.size());
        return new FloorMapResult(floor, scanId, buildJobId, nodes, edges, polygons, etag);
    }

    private List<FloorAreaPolygonEntity> polygons(UUID scanId, Optional<UUID> areaId) {
        if (scanId == null) {
            return List.of();
        }
        return areaId
                .map(polygonRepository::findByFloorArea_AreaId)
                .orElseGet(() -> polygonRepository.findByScanId(scanId));
    }

    private String etagFor(
            UUID floorId, UUID scanId, UUID buildJobId, int nodeCount, int edgeCount, int polygonCount
    ) {
        return Integer.toHexString(Objects.hash(floorId, scanId, buildJobId, nodeCount, edgeCount, polygonCount));
    }

    public record FloorMapResult(
            FloorEntity floor,
            UUID scanId,
            UUID buildJobId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<FloorAreaPolygonEntity> polygons,
            String etag
    ) {
    }
}
