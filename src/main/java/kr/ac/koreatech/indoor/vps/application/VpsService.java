package kr.ac.koreatech.indoor.vps.application;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface VpsService {
    List<BuildingResponse> listBuildings(String statusFilter);

    BuildingResponse createBuilding(BuildingCreateRequest request);

    BuildingDetailResponse getBuilding(UUID buildingId);

    BuildingResponse updateBuilding(UUID buildingId, BuildingUpdateRequest request);

    void deleteBuilding(UUID buildingId);

    BuildingResponse patchStatus(UUID buildingId, BuildingStatusRequest request);

    List<FloorResponse> listFloors(UUID buildingId);

    FloorResponse createFloor(UUID buildingId, FloorCreateRequest request);

    FloorResponse getFloor(UUID floorId);

    FloorResponse updateFloor(UUID floorId, FloorUpdateRequest request);

    void deleteFloor(UUID floorId);

    FloorPathResponse getFloorPath(UUID floorId);

    FloorMapResponse getFloorMap(UUID floorId);

    ScanChunkResponse uploadScanChunk(UUID floorId, MultipartFile upload, String scanIdText, String deviceInfo, boolean force);

    List<ScanChunkResponse> listScanChunks(UUID floorId);

    void deleteScanChunk(UUID floorId, UUID chunkId);

    MergedScanResponse mergeScans(UUID floorId, List<UUID> chunkIds);

    MergedScanResponse mergeStatus(UUID floorId);

    ProcessingStatusResponse process(UUID floorId);

    ProcessingStatusResponse processStatus(UUID floorId);

    PathfindingResponse pathfinding(UUID buildingId, PathfindingRequest request);

    Map<String, Object> floorRoute(UUID floorId, UUID fromNode, UUID toNode);

    List<POIResponse> listPois(UUID buildingId);

    List<POIResponse> searchPois(UUID buildingId, String query);
}
