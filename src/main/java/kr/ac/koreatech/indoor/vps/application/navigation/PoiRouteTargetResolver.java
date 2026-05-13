package kr.ac.koreatech.indoor.vps.application.navigation;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.PoiCanonicalRepository;
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

    public PoiRouteTarget find(UUID buildingId, String destinationName) {
        if (destinationName == null || destinationName.isBlank()) {
            return null;
        }
        return poiCanonicalRepository.search(buildingId, "%" + destinationName.toLowerCase() + "%").stream()
                .map(this::toTarget)
                .findFirst()
                .orElse(null);
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
