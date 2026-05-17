package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PoiUseCase {
    private final BuildingQueryService buildingQuery;
    private final PoiCanonicalRepository poiCanonicalRepository;

    public PoiUseCase(
            BuildingQueryService buildingQuery,
            PoiCanonicalRepository poiCanonicalRepository
    ) {
        this.buildingQuery = buildingQuery;
        this.poiCanonicalRepository = poiCanonicalRepository;
    }

    public List<PoiResult> listPois(UUID buildingId) {
        buildingQuery.requireBuilding(buildingId);
        return poiCanonicalRepository.findByBuilding_BuildingIdOrderByNameAscLabelAsc(buildingId).stream()
                .map(this::toPoiResult)
                .toList();
    }

    public List<PoiResult> searchPois(UUID buildingId, String query) {
        buildingQuery.requireBuilding(buildingId);
        if (query == null || query.isBlank()) {
            return listPois(buildingId);
        }
        return poiCanonicalRepository.search(buildingId, "%" + query.toLowerCase() + "%").stream()
                .map(this::toPoiResult)
                .toList();
    }

    private PoiResult toPoiResult(PoiCanonicalEntity poi) {
        Point point = firstPoint(poi);
        Map<String, Double> displayPoint = point == null
                ? null
                : Map.of("x", x(point), "y", y(point), "z", z(point));
        return new PoiResult(
                poi.getCanonicalId(),
                poi.getBuilding() == null ? null : poi.getBuilding().getBuildingId(),
                poi.getFloor() == null ? null : poi.getFloor().getFloorId(),
                poi.getName(),
                poi.getLabel(),
                poi.getCategory(),
                poi.getRouteNodeId(),
                displayPoint,
                poi.isNeedsReview(),
                poi.getLlmConfidence()
        );
    }

    private Point firstPoint(PoiCanonicalEntity poi) {
        return poi.getDisplayPoint() != null ? poi.getDisplayPoint() : poi.getWorldPose();
    }

    private double x(Point point) {
        return point.getX();
    }

    private double y(Point point) {
        return point.getY();
    }

    private double z(Point point) {
        Coordinate coordinate = point.getCoordinate();
        return Double.isNaN(coordinate.getZ()) ? 0.0 : coordinate.getZ();
    }
}
