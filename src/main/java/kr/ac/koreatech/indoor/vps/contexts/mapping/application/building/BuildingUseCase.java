package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.BuildingDtos.*;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.building.BuildingStatus;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildingUseCase {
    private final BuildingRepository buildingRepository;
    private final FloorUseCase floors;

    public BuildingUseCase(
            BuildingRepository buildingRepository,
            FloorUseCase floors
    ) {
        this.buildingRepository = buildingRepository;
        this.floors = floors;
    }

    public List<BuildingResponse> listBuildings(String statusFilter) {
        List<BuildingEntity> buildings = statusFilter == null || statusFilter.isBlank()
                ? buildingRepository.findAllByOrderByCreatedAtAsc()
                : buildingRepository.findByStatusOrderByCreatedAtAsc(statusFilter);
        return buildings.stream().map(this::toBuildingResponse).toList();
    }

    @Transactional
    public BuildingResponse createBuilding(BuildingCreateRequest request) {
        BuildingEntity building = new BuildingEntity(
                request.name(),
                request.description(),
                request.latitude(),
                request.longitude()
        );
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    public BuildingDetailResponse getBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        return new BuildingDetailResponse(
                building.getBuildingId(),
                building.getName(),
                building.getDescription(),
                building.getLatitude(),
                building.getLongitude(),
                BuildingStatus.valueOf(building.getStatus()),
                building.getCreatedAt(),
                building.getUpdatedAt(),
                floors.listFloors(buildingId),
                List.of()
        );
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        building.updateProfile(request.name(), request.description(), request.latitude(), request.longitude());
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    @Transactional
    public void deleteBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        buildingRepository.delete(building);
        buildingRepository.flush();
    }

    @Transactional
    public BuildingResponse patchStatus(UUID buildingId, BuildingStatusRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        building.changeStatus(request.status().name());
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    public BuildingEntity requireBuilding(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> notFound("BUILDING_NOT_FOUND", "building not found"));
    }

    private BuildingResponse toBuildingResponse(BuildingEntity building) {
        return new BuildingResponse(
                building.getBuildingId(),
                building.getName(),
                building.getDescription(),
                building.getLatitude(),
                building.getLongitude(),
                BuildingStatus.valueOf(building.getStatus()),
                building.getCreatedAt(),
                building.getUpdatedAt()
        );
    }

    private ClientApiException notFound(String code, String message) {
        return new ClientApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
