package kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
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
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final FloorScanRepository floorScanRepository;
    private final FloorQueryService floorQueryService;

    public FloorUseCase(
            BuildingRepository buildingRepository,
            FloorRepository floorRepository,
            FloorScanRepository floorScanRepository,
            FloorQueryService floorQueryService
    ) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.floorScanRepository = floorScanRepository;
        this.floorQueryService = floorQueryService;
    }

    @Transactional
    public FloorResult createFloor(UUID buildingId, FloorCreateCommand command) {
        BuildingEntity building = requireBuilding(buildingId);
        try {
            FloorEntity floor = new FloorEntity(building, command.name(), command.level(), command.height());
            return floorQueryService.toFloorResult(floorRepository.saveAndFlush(floor));
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

    private BuildingEntity requireBuilding(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "BUILDING_NOT_FOUND", "building not found"));
    }
}
