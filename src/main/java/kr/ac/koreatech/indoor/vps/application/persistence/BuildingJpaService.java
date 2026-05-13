package kr.ac.koreatech.indoor.vps.application.persistence;

import static kr.ac.koreatech.indoor.vps.api.dto.BuildingDtos.*;
import static kr.ac.koreatech.indoor.vps.api.dto.FloorDtos.*;
import static kr.ac.koreatech.indoor.vps.api.dto.PassageDtos.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.BuildingRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class BuildingJpaService {
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final FloorScanRepository floorScanRepository;
    private final MapNodeRepository mapNodeRepository;

    public BuildingJpaService(
            BuildingRepository buildingRepository,
            FloorRepository floorRepository,
            FloorScanRepository floorScanRepository,
            MapNodeRepository mapNodeRepository
    ) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.floorScanRepository = floorScanRepository;
        this.mapNodeRepository = mapNodeRepository;
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
                listFloors(buildingId),
                List.of()
        );
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        if (request.name() != null) {
            building.setName(request.name());
        }
        if (request.description() != null) {
            building.setDescription(request.description());
        }
        if (request.latitude() != null) {
            building.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            building.setLongitude(request.longitude());
        }
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
        building.setStatus(request.status().name());
        return toBuildingResponse(buildingRepository.saveAndFlush(building));
    }

    public List<FloorResponse> listFloors(UUID buildingId) {
        requireBuilding(buildingId);
        return floorRepository.findByBuilding_BuildingIdOrderByLevelAscNameAsc(buildingId).stream()
                .map(this::toFloorResponse)
                .toList();
    }

    @Transactional
    public FloorResponse createFloor(UUID buildingId, FloorCreateRequest request) {
        BuildingEntity building = requireBuilding(buildingId);
        try {
            FloorEntity floor = new FloorEntity(building, request.name(), request.level(), request.height());
            return toFloorResponse(floorRepository.saveAndFlush(floor));
        } catch (DataIntegrityViolationException e) {
            throw new ClientApiException(HttpStatus.CONFLICT, "FLOOR_CONFLICT", "floor level already exists");
        }
    }

    public FloorResponse getFloor(UUID floorId) {
        return toFloorResponse(requireFloor(floorId));
    }

    @Transactional
    public FloorResponse updateFloor(UUID floorId, FloorUpdateRequest request) {
        FloorEntity floor = requireFloor(floorId);
        if (request.name() != null) {
            floor.setName(request.name());
        }
        if (request.height() != null) {
            floor.setHeight(request.height());
        }
        return toFloorResponse(floorRepository.saveAndFlush(floor));
    }

    @Transactional
    public void deleteFloor(UUID floorId) {
        FloorEntity floor = requireFloor(floorId);
        floorRepository.delete(floor);
        floorRepository.flush();
    }

    public BuildingEntity requireBuilding(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> notFound("BUILDING_NOT_FOUND", "building not found"));
    }

    public FloorEntity requireFloor(UUID floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> notFound("FLOOR_NOT_FOUND", "floor not found"));
    }

    public Optional<FloorScanEntity> activeScan(UUID floorId) {
        return floorScanRepository.findFirstByFloor_FloorIdAndActiveTrueOrderByCreatedAtDesc(floorId);
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

    private FloorResponse toFloorResponse(FloorEntity floor) {
        Optional<FloorScanEntity> active = activeScan(floor.getFloorId());
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        return new FloorResponse(
                floor.getFloorId(),
                floor.getBuilding().getBuildingId(),
                floor.getName(),
                floor.getLevel(),
                floor.getHeight(),
                scanId != null && mapNodeRepository.existsByScanIdAndStaleFalse(scanId),
                false,
                scanId,
                floor.getCreatedAt(),
                floor.getUpdatedAt()
        );
    }

    private ClientApiException notFound(String code, String message) {
        return new ClientApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
