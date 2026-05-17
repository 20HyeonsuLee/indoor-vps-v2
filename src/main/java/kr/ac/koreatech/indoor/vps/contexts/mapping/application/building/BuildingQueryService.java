package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
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
public class BuildingQueryService {
    private final BuildingRepository buildingRepository;
    private final FloorQueryService floorQueryService;

    public BuildingQueryService(BuildingRepository buildingRepository, FloorQueryService floorQueryService) {
        this.buildingRepository = buildingRepository;
        this.floorQueryService = floorQueryService;
    }

    public BuildingEntity requireBuilding(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "BUILDING_NOT_FOUND", "building not found"));
    }

    public List<BuildingResult.Summary> listBuildings(String statusFilter) {
        List<BuildingEntity> buildings = statusFilter == null || statusFilter.isBlank()
                ? buildingRepository.findAllByOrderByCreatedAtAsc()
                : buildingRepository.findByStatusOrderByCreatedAtAsc(statusFilter);
        return buildings.stream().map(this::toSummary).toList();
    }

    public BuildingResult.Detail getBuilding(UUID buildingId) {
        BuildingEntity building = requireBuilding(buildingId);
        return new BuildingResult.Detail(
                building.getBuildingId(),
                building.getName(),
                building.getDescription(),
                building.getLatitude(),
                building.getLongitude(),
                BuildingStatus.valueOf(building.getStatus()),
                building.getCreatedAt(),
                building.getUpdatedAt(),
                floorQueryService.listFloors(buildingId),
                List.of()
        );
    }

    BuildingResult.Summary toSummary(BuildingEntity building) {
        return new BuildingResult.Summary(
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
}
