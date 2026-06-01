package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.BuildJobRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class MergeBuildUseCase {

    private final FloorQueryService floorQueryService;
    private final ScanPersistence scanPersistence;
    private final BuildJobRepository buildJobRepository;

    public MergeBuildUseCase(
            FloorQueryService floorQueryService,
            ScanPersistence scanPersistence,
            BuildJobRepository buildJobRepository
    ) {
        this.floorQueryService = floorQueryService;
        this.scanPersistence = scanPersistence;
        this.buildJobRepository = buildJobRepository;
    }

    @Transactional
    public MergeBuildResult enqueueMergeBuild(MergeBuildCommand command) {
        floorQueryService.requireFloor(command.floorId());
        validateScanPaths(command.scanPaths());

        FloorAreaEntity area = floorQueryService.defaultArea(command.floorId())
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "DEFAULT_AREA_NOT_FOUND", "floor has no default area"));

        UUID mergedScanId = UUID.randomUUID();
        Map<String, Object> deviceInfo = mergeSourceDeviceInfo(command.scanPaths());

        ScanIngestEntity scan = scanPersistence.saveScan(new ScanIngestEntity(
                mergedScanId,
                "pending-merge",
                "scans/" + mergedScanId,
                deviceInfo,
                area.getAreaId()
        ));
        scan.changeBuildState(BuildState.pending);

        scanPersistence.deactivateForFloor(command.floorId());

        FloorScanEntity floorScan = new FloorScanEntity(
                area,
                scan,
                "merged_" + mergedScanId + ".db",
                null,
                scanPersistence.nextUploadOrder(command.floorId())
        );
        floorScan.changeStatus("MERGE_PENDING");
        floorScan.changeActive(true);
        scanPersistence.saveScanEntity(floorScan);

        BuildJobEntity job = buildJobRepository.saveAndFlush(new BuildJobEntity(scan));
        scan.attachBuildJob(job.getBuildJobId());
        scanPersistence.saveScan(scan);

        return new MergeBuildResult(command.floorId(), mergedScanId, job.getBuildJobId(), "QUEUED");
    }

    private void validateScanPaths(List<String> scanPaths) {
        if (scanPaths == null || scanPaths.size() < 2) {
            throw new ClientApiException(
                    HttpStatus.BAD_REQUEST, "SCAN_PATHS_INSUFFICIENT", "at least 2 scan paths are required");
        }
        for (String rawPath : scanPaths) {
            if (!Files.exists(Path.of(rawPath))) {
                throw new ClientApiException(
                        HttpStatus.BAD_REQUEST, "SCAN_PATH_NOT_FOUND", "scan file not found: " + rawPath);
            }
        }
    }

    private Map<String, Object> mergeSourceDeviceInfo(List<String> scanPaths) {
        Map<String, Object> deviceInfo = new LinkedHashMap<>();
        deviceInfo.put("merge", Map.of("sources", scanPaths));
        return deviceInfo;
    }
}
