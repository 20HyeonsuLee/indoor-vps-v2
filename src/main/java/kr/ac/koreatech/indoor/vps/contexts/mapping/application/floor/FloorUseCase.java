package kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.FloorDtos.*;

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
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FloorUseCase {
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final FloorScanRepository floorScanRepository;
    private final MapNodeRepository mapNodeRepository;

    public FloorUseCase(
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
        floor.updateProfile(request.name(), request.height());
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
