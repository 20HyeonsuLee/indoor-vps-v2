package kr.ac.koreatech.indoor.vps.application;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.persistence.BuildingJpaService;
import kr.ac.koreatech.indoor.vps.application.persistence.NavigationJpaService;
import kr.ac.koreatech.indoor.vps.application.persistence.PoiJpaService;
import kr.ac.koreatech.indoor.vps.application.persistence.ScanJpaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class JpaVpsService implements VpsService {
    private final BuildingJpaService buildings;
    private final ScanJpaService scans;
    private final NavigationJpaService navigation;
    private final PoiJpaService pois;

    public JpaVpsService(
            BuildingJpaService buildings,
            ScanJpaService scans,
            NavigationJpaService navigation,
            PoiJpaService pois
    ) {
        this.buildings = buildings;
        this.scans = scans;
        this.navigation = navigation;
        this.pois = pois;
    }

    @Override
    public List<BuildingResponse> listBuildings(String statusFilter) {
        return buildings.listBuildings(statusFilter);
    }

    @Override
    public BuildingResponse createBuilding(BuildingCreateRequest request) {
        return buildings.createBuilding(request);
    }

    @Override
    public BuildingDetailResponse getBuilding(UUID buildingId) {
        return buildings.getBuilding(buildingId);
    }

    @Override
    public BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request) {
        return buildings.updateBuilding(buildingId, request);
    }

    @Override
    public void deleteBuilding(UUID buildingId) {
        buildings.deleteBuilding(buildingId);
    }

    @Override
    public BuildingResponse patchStatus(UUID buildingId, BuildingStatusRequest request) {
        return buildings.patchStatus(buildingId, request);
    }

    @Override
    public List<FloorResponse> listFloors(UUID buildingId) {
        return buildings.listFloors(buildingId);
    }

    @Override
    public FloorResponse createFloor(UUID buildingId, FloorCreateRequest request) {
        return buildings.createFloor(buildingId, request);
    }

    @Override
    public FloorResponse getFloor(UUID floorId) {
        return buildings.getFloor(floorId);
    }

    @Override
    public FloorResponse updateFloor(UUID floorId, FloorUpdateRequest request) {
        return buildings.updateFloor(floorId, request);
    }

    @Override
    public void deleteFloor(UUID floorId) {
        buildings.deleteFloor(floorId);
    }

    @Override
    public FloorPathResponse getFloorPath(UUID floorId) {
        return navigation.getFloorPath(floorId);
    }

    @Override
    public FloorMapResponse getFloorMap(UUID floorId) {
        return navigation.getFloorMap(floorId);
    }

    @Override
    public ScanChunkResponse uploadScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanIdText,
            String deviceInfo,
            boolean force
    ) {
        return scans.uploadScanChunk(floorId, upload, scanIdText, deviceInfo, force);
    }

    @Override
    public List<ScanChunkResponse> listScanChunks(UUID floorId) {
        return scans.listScanChunks(floorId);
    }

    @Override
    public void deleteScanChunk(UUID floorId, UUID chunkId) {
        scans.deleteScanChunk(floorId, chunkId);
    }

    @Override
    public MergedScanResponse mergeScans(UUID floorId, List<UUID> chunkIds) {
        return scans.mergeScans(floorId, chunkIds);
    }

    @Override
    public MergedScanResponse mergeStatus(UUID floorId) {
        return scans.mergeStatus(floorId);
    }

    @Override
    public ProcessingStatusResponse process(UUID floorId) {
        return scans.process(floorId);
    }

    @Override
    public ProcessingStatusResponse processStatus(UUID floorId) {
        return scans.processStatus(floorId);
    }

    @Override
    public PathfindingResponse pathfinding(UUID buildingId, PathfindingRequest request) {
        return navigation.pathfinding(buildingId, request);
    }

    @Override
    public Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode) {
        return navigation.floorRoute(floorId, fromNode, toNode);
    }

    @Override
    public List<POIResponse> listPois(UUID buildingId) {
        return pois.listPois(buildingId);
    }

    @Override
    public List<POIResponse> searchPois(UUID buildingId, String query) {
        return pois.searchPois(buildingId, query);
    }
}
