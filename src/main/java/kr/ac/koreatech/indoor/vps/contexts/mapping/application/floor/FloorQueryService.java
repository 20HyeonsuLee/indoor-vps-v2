package kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.MapNodeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FloorQueryService {
    private final FloorRepository floorRepository;
    private final FloorAreaRepository floorAreaRepository;
    private final FloorScanRepository floorScanRepository;
    private final MapNodeRepository mapNodeRepository;
    private final IndoorProperties properties;

    public FloorQueryService(
            FloorRepository floorRepository,
            FloorAreaRepository floorAreaRepository,
            FloorScanRepository floorScanRepository,
            MapNodeRepository mapNodeRepository,
            IndoorProperties properties
    ) {
        this.floorRepository = floorRepository;
        this.floorAreaRepository = floorAreaRepository;
        this.floorScanRepository = floorScanRepository;
        this.mapNodeRepository = mapNodeRepository;
        this.properties = properties;
    }

    public FloorEntity requireFloor(UUID floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.NOT_FOUND, "FLOOR_NOT_FOUND", "floor not found"));
    }

    public Optional<FloorAreaEntity> defaultArea(UUID floorId) {
        return floorAreaRepository.findByFloor_FloorIdAndIsDefaultTrue(floorId);
    }

    public Optional<FloorAreaEntity> area(UUID areaId) {
        return floorAreaRepository.findById(areaId);
    }

    public Optional<FloorScanEntity> activeScan(UUID floorId) {
        return defaultArea(floorId)
                .flatMap(area -> floorScanRepository.findFirstByArea_AreaIdAndActiveTrueOrderByCreatedAtDesc(area.getAreaId()));
    }

    public Optional<FloorScanEntity> activeScanForArea(UUID floorId, Optional<UUID> areaId) {
        if (areaId.isEmpty()) {
            return activeScan(floorId);
        }
        return floorScanRepository.findFirstByArea_AreaIdAndActiveTrueOrderByCreatedAtDesc(areaId.get());
    }

    public FloorResult getFloor(UUID floorId) {
        return toFloorResult(requireFloor(floorId));
    }

    public List<FloorResult> listFloors(UUID buildingId) {
        return floorRepository.findByBuilding_BuildingIdOrderByLevelAscNameAsc(buildingId).stream()
                .map(this::toFloorResult)
                .toList();
    }

    FloorResult toFloorResult(FloorEntity floor) {
        Optional<FloorScanEntity> active = activeScan(floor.getFloorId());
        UUID scanId = active.map(scan -> scan.getScan().getScanId()).orElse(null);
        return new FloorResult(
                floor.getFloorId(),
                floor.getBuilding().getBuildingId(),
                floor.getName(),
                floor.getLevel(),
                floor.getHeight(),
                scanId != null && mapNodeRepository.existsByScanIdAndStaleFalse(scanId),
                active.map(this::hasPly).orElse(false),
                scanId,
                floor.getCreatedAt(),
                floor.getUpdatedAt()
        );
    }

    private boolean hasPly(FloorScanEntity floorScan) {
        String storagePath = floorScan.getScan().getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        Path scanDir = fileName != null && "rtabmap.db".equals(fileName.toString())
                ? path.getParent()
                : path;
        return scanDir != null && Files.isRegularFile(scanDir.resolve("cloud.ply"));
    }
}
