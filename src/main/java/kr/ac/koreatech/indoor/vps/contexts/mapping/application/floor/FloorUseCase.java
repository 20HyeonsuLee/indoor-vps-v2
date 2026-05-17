package kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Floor CRUD — read helpers delegated to FloorQueryService.
 * public methods: createFloor, updateFloor, deleteFloor, requireFloor, activeScan = 5
 */
@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FloorUseCase {
    private final BuildingQueryService buildingQuery;
    private final FloorRepository floorRepository;
    private final FloorAreaRepository floorAreaRepository;
    private final FloorQueryService floorQueryService;

    public FloorUseCase(
            BuildingQueryService buildingQuery,
            FloorRepository floorRepository,
            FloorAreaRepository floorAreaRepository,
            FloorQueryService floorQueryService
    ) {
        this.buildingQuery = buildingQuery;
        this.floorRepository = floorRepository;
        this.floorAreaRepository = floorAreaRepository;
        this.floorQueryService = floorQueryService;
    }

    @Transactional
    public FloorResult createFloor(UUID buildingId, FloorCreateCommand command) {
        BuildingEntity building = buildingQuery.requireBuilding(buildingId);
        try {
            FloorEntity floor = new FloorEntity(building, command.name(), command.level(), command.height());
            FloorEntity saved = floorRepository.saveAndFlush(floor);
            floorAreaRepository.saveAndFlush(FloorAreaEntity.createDefault(saved));
            return floorQueryService.toFloorResult(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ClientApiException(HttpStatus.CONFLICT, "FLOOR_CONFLICT", "floor level already exists");
        }
    }

    @Transactional
    public FloorResult updateFloor(UUID floorId, FloorUpdateCommand command) {
        FloorEntity floor = requireFloor(floorId);
        floor.updateProfile(command.name(), command.height());
        return floorQueryService.toFloorResult(floorRepository.saveAndFlush(floor));
    }

    @Transactional
    public void deleteFloor(UUID floorId) {
        FloorEntity floor = requireFloor(floorId);
        floorRepository.delete(floor);
        floorRepository.flush();
    }

    public FloorEntity requireFloor(UUID floorId) {
        return floorQueryService.requireFloor(floorId);
    }

    public Optional<FloorScanEntity> activeScan(UUID floorId) {
        return floorQueryService.activeScan(floorId);
    }
}
