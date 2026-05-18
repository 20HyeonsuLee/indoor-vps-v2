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
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class GetFloorMapUseCase {
    private final FloorQueryService floorQuery;
    private final GraphQueryFacade graphQueryFacade;

    public GetFloorMapUseCase(FloorQueryService floorQuery, GraphQueryFacade graphQueryFacade) {
        this.floorQuery = floorQuery;
        this.graphQueryFacade = graphQueryFacade;
    }

    public FloorMapResult getFloorMap(UUID floorId, Optional<UUID> areaId) {
        FloorEntity floor = floorQuery.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorQuery.activeScanForArea(floorId, areaId);
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        UUID areaIdResolved = active.map(scan -> scan.getArea().getAreaId()).orElse(null);
        UUID buildingIdResolved = floor.getBuilding().getBuildingId();
        UUID buildJobId = scanId == null ? null : graphQueryFacade.latestBuildJobId(scanId).orElse(null);
        List<MapNodeEntity> nodes = scanId == null ? List.of() : graphQueryFacade.nodeEntities(scanId);
        List<MapEdgeEntity> edges = scanId == null ? List.of() : graphQueryFacade.edgeEntities(scanId);
        List<FloorAreaPolygonEntity> polygons = scanId == null ? List.of() : graphQueryFacade.polygonEntities(scanId);
        List<PoiCanonicalEntity> pois = scanId == null ? List.of() : graphQueryFacade.poisForScan(scanId);
        List<VerticalConnectorStopEntity> stopsInArea = areaIdResolved == null
                ? List.of() : graphQueryFacade.stopsForArea(areaIdResolved);
        // Sibling stops (다른 area에 있는 같은 connector의 stop들)을 함께 보낸다.
        List<VerticalConnectorStopEntity> stopsInBuilding = graphQueryFacade.stopsForBuilding(buildingIdResolved);
        String etag = etagFor(floor.getFloorId(), scanId, buildJobId,
                nodes.size(), edges.size(), polygons.size(), pois.size(), stopsInArea.size());
        return new FloorMapResult(floor, scanId, buildJobId, nodes, edges, polygons,
                pois, stopsInArea, stopsInBuilding, etag);
    }

    private String etagFor(UUID floorId, UUID scanId, UUID buildJobId, int nodeCount,
            int edgeCount, int polygonCount, int poiCount, int stopCount) {
        return Integer.toHexString(Objects.hash(
                floorId, scanId, buildJobId, nodeCount, edgeCount, polygonCount, poiCount, stopCount));
    }

    public record FloorMapResult(
            FloorEntity floor,
            UUID scanId,
            UUID buildJobId,
            List<MapNodeEntity> nodes,
            List<MapEdgeEntity> edges,
            List<FloorAreaPolygonEntity> polygons,
            List<PoiCanonicalEntity> pois,
            List<VerticalConnectorStopEntity> stopsInArea,
            List<VerticalConnectorStopEntity> stopsInBuilding,
            String etag
    ) {
    }
}
