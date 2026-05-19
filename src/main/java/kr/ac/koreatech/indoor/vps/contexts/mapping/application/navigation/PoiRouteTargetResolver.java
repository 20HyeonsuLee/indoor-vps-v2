package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PoiRouteTargetResolver {
    private final PoiCanonicalRepository poiCanonicalRepository;

    public PoiRouteTargetResolver(PoiCanonicalRepository poiCanonicalRepository) {
        this.poiCanonicalRepository = poiCanonicalRepository;
    }

    public Optional<PoiRouteTarget> find(UUID buildingId, String destinationName) {
        if (destinationName == null || destinationName.isBlank()) {
            return Optional.empty();
        }
        return poiCanonicalRepository.searchActive(buildingId, "%" + destinationName.toLowerCase() + "%").stream()
                .map(this::toTarget)
                .findFirst();
    }

    public Optional<PoiRouteTarget> findById(UUID buildingId, UUID destinationId) {
        if (destinationId == null) {
            return Optional.empty();
        }
        return poiCanonicalRepository
                .findByCanonicalIdAndBuilding_BuildingId(destinationId, buildingId)
                .map(this::toTarget);
    }

    private PoiRouteTarget toTarget(PoiCanonicalEntity poi) {
        Point point = firstPoint(poi);
        return new PoiRouteTarget(
                poi.getRouteNodeId(),
                point == null ? 0.0 : NavigationGeometry.x(point),
                point == null ? 0.0 : NavigationGeometry.y(point),
                point == null ? 0.0 : NavigationGeometry.z(point),
                poi.getFloor() == null ? null : poi.getFloor().getLevel()
        );
    }

    private Point firstPoint(PoiCanonicalEntity poi) {
        return poi.getDisplayPoint() != null ? poi.getDisplayPoint() : poi.getWorldPose();
    }

    public record PoiRouteTarget(UUID routeNodeId, double x, double y, double z, Integer floorLevel) {
    }
}
