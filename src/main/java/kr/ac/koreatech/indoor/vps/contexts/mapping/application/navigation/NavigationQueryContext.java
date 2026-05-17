package kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PoiRouteTargetResolver.PoiRouteTarget;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BuildingQueryService + FloorQueryService + PoiRouteTargetResolver 조합 조회를 캡슐화.
 * PlanRouteUseCase instance_vars 3 이하 유지를 위해 도입.
 */
@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class NavigationQueryContext {

    private final BuildingQueryService buildingQuery;
    private final FloorQueryService floorQuery;
    private final PoiRouteTargetResolver targetResolver;

    public NavigationQueryContext(
            BuildingQueryService buildingQuery,
            FloorQueryService floorQuery,
            PoiRouteTargetResolver targetResolver
    ) {
        this.buildingQuery = buildingQuery;
        this.floorQuery = floorQuery;
        this.targetResolver = targetResolver;
    }

    public void requireBuilding(UUID buildingId) {
        buildingQuery.requireBuilding(buildingId);
    }

    public void requireFloor(UUID floorId) {
        floorQuery.requireFloor(floorId);
    }

    public Optional<FloorScanEntity> activeScan(UUID floorId) {
        return floorQuery.activeScan(floorId);
    }

    public Optional<FloorScanEntity> activeScanForArea(UUID floorId, Optional<UUID> areaId) {
        return floorQuery.activeScanForArea(floorId, areaId);
    }

    public Optional<PoiRouteTarget> findTarget(UUID buildingId, String destinationName) {
        return targetResolver.find(buildingId, destinationName);
    }
}
