package kr.ac.koreatech.indoor.vps.application.persistence;

import static kr.ac.koreatech.indoor.vps.api.dto.PoiDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.PoiCanonicalRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PoiJpaService {
    private final BuildingJpaService buildingService;
    private final PoiCanonicalRepository poiCanonicalRepository;

    public PoiJpaService(
            BuildingJpaService buildingService,
            PoiCanonicalRepository poiCanonicalRepository
    ) {
        this.buildingService = buildingService;
        this.poiCanonicalRepository = poiCanonicalRepository;
    }

    public List<POIResponse> listPois(UUID buildingId) {
        buildingService.requireBuilding(buildingId);
        return poiCanonicalRepository.findByBuilding_BuildingIdOrderByNameAscLabelAsc(buildingId).stream()
                .map(this::toPoiResponse)
                .toList();
    }

    public List<POIResponse> searchPois(UUID buildingId, String query) {
        buildingService.requireBuilding(buildingId);
        if (query == null || query.isBlank()) {
            return listPois(buildingId);
        }
        return poiCanonicalRepository.search(buildingId, "%" + query.toLowerCase() + "%").stream()
                .map(this::toPoiResponse)
                .toList();
    }

    private POIResponse toPoiResponse(PoiCanonicalEntity poi) {
        Point point = firstPoint(poi);
        Map<String, Double> displayPoint = point == null
                ? null
                : Map.of("x", x(point), "y", y(point), "z", z(point));
        return new POIResponse(
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
