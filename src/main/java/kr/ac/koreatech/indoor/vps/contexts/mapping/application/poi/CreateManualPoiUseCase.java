package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScope;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ManualEditScopeResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.PoiCanonicalRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class CreateManualPoiUseCase {

    private final PoiCanonicalRepository poiRepository;
    private final ManualEditScopeResolver scopeResolver;
    private final PoiGeometryFactory poiGeometryFactory;

    public CreateManualPoiUseCase(
            PoiCanonicalRepository poiRepository,
            ManualEditScopeResolver scopeResolver,
            PoiGeometryFactory poiGeometryFactory
    ) {
        this.poiRepository = poiRepository;
        this.scopeResolver = scopeResolver;
        this.poiGeometryFactory = poiGeometryFactory;
    }

    @Transactional
    public PoiCanonicalEntity create(UUID buildingId, CreatePoiCommand command) {
        ManualEditScope scope = scopeResolver.resolve(command.areaId());
        FloorAreaEntity area = scope.area();
        BuildingEntity building = area.getFloor().getBuilding();
        if (!building.getBuildingId().equals(buildingId)) {
            throw new ClientApiException(
                    HttpStatus.BAD_REQUEST,
                    "AREA_BUILDING_MISMATCH",
                    "area " + command.areaId() + " does not belong to building " + buildingId);
        }
        Point worldPose = poiGeometryFactory.point(command.x(), command.y(), command.z());
        Point displayPoint = poiGeometryFactory.point(
                command.displayX().orElse(command.x()),
                command.displayY().orElse(command.y()),
                command.displayZ().orElse(command.z())
        );
        PoiCanonicalEntity poi = PoiCanonicalEntity.createManual(
                UUID.randomUUID(),
                scope.scanId(),
                building,
                area.getFloor(),
                area,
                command.name(),
                command.category(),
                worldPose,
                displayPoint,
                command.routeNodeId().orElse(null)
        );
        return poiRepository.saveAndFlush(poi);
    }
}
