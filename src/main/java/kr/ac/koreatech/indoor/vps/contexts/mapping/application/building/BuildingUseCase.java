package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.building.BuildingStatus;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Building mutations — reads delegated to BuildingQueryService.
 * public methods: createBuilding, updateBuilding, deleteBuilding, patchStatus, requireBuilding = 5
 */
@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildingUseCase {
    private final BuildingRepository buildingRepository;
    private final BuildingQueryService buildingQueryService;

    public BuildingUseCase(
            BuildingRepository buildingRepository,
            BuildingQueryService buildingQueryService
    ) {
        this.buildingRepository = buildingRepository;
        this.buildingQueryService = buildingQueryService;
    }

    @Transactional
    public BuildingResult.Summary createBuilding(BuildingCreateCommand command) {
        BuildingEntity building = new BuildingEntity(
                command.name(),
                command.description(),
                command.latitude(),
                command.longitude()
        );
        return buildingQueryService.toSummary(buildingRepository.saveAndFlush(building));
    }

    @Transactional
    public BuildingResult.Summary updateBuilding(UUID buildingId, BuildingUpdateCommand command) {
        BuildingEntity building = requireBuilding(buildingId);
        building.updateProfile(command.name(), command.description(), command.latitude(), command.longitude());
        return buildingQueryService.toSummary(buildingRepository.saveAndFlush(building));
    }

    @Transactional
    public void deleteBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        buildingRepository.delete(building);
        buildingRepository.flush();
    }

    @Transactional
    public BuildingResult.Summary patchStatus(UUID buildingId, BuildingStatus status) {
        BuildingEntity building = requireBuilding(buildingId);
        building.changeStatus(status.name());
        return buildingQueryService.toSummary(buildingRepository.saveAndFlush(building));
    }

    public BuildingEntity requireBuilding(UUID buildingId) {
        return buildingQueryService.requireBuilding(buildingId);
    }
}
