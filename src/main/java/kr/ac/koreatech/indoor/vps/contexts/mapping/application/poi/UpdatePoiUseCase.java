package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class UpdatePoiUseCase {

    private final PoiCanonicalRepository poiRepository;
    private final PoiGeometryFactory poiGeometryFactory;

    public UpdatePoiUseCase(PoiCanonicalRepository poiRepository, PoiGeometryFactory poiGeometryFactory) {
        this.poiRepository = poiRepository;
        this.poiGeometryFactory = poiGeometryFactory;
    }

    @Transactional
    public PoiCanonicalEntity update(UUID poiId, UpdatePoiCommand command) {
        PoiCanonicalEntity poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "POI_NOT_FOUND", "poi not found: " + poiId));
        poi.rename(command.name().orElse(null), command.category().orElse(null), command.label().orElse(null));
        applyGeom(poi, command);
        if (command.detachRouteNode().orElse(false)) {
            poi.detachRouteNode();
        }
        command.routeNodeId().ifPresent(poi::attachRouteNode);
        if (command.markReviewed().orElse(false)) {
            poi.markReviewed();
        }
        return poiRepository.saveAndFlush(poi);
    }

    private void applyGeom(PoiCanonicalEntity poi, UpdatePoiCommand command) {
        Point newWorld = mergePoint(poi.getWorldPose(), command.x(), command.y(), command.z());
        Point newDisplay = mergePoint(poi.getDisplayPoint(), command.displayX(), command.displayY(), command.displayZ());
        poi.relocate(newWorld, newDisplay);
    }

    private Point mergePoint(
            Point current,
            Optional<Double> x,
            Optional<Double> y,
            Optional<Double> z
    ) {
        if (x.isEmpty() && y.isEmpty() && z.isEmpty()) {
            return null;
        }
        Coordinate base = current != null ? current.getCoordinate() : new Coordinate(0, 0, 0);
        return poiGeometryFactory.point(
                x.orElse(base.getX()),
                y.orElse(base.getY()),
                z.orElse(Double.isNaN(base.getZ()) ? 0.0 : base.getZ())
        );
    }
}
