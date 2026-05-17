package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.build.BuildState;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildJobEntity;
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
public class ProcessFloorUseCase {

    private final FloorQueryService floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final BuildJobRepository buildJobRepository;

    public ProcessFloorUseCase(
            FloorQueryService floorService,
            ScanIngestRepository scanIngestRepository,
            BuildJobRepository buildJobRepository
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.buildJobRepository = buildJobRepository;
    }

    @Transactional
    public ProcessingStatusResult process(UUID floorId, Optional<UUID> areaId) {
        floorService.requireFloor(floorId);
        FloorScanEntity active = floorService.activeScanForArea(floorId, areaId)
                .orElseThrow(() -> new ClientApiException(HttpStatus.CONFLICT, "ACTIVE_SCAN_NOT_FOUND", "floor has no active scan"));
        ScanIngestEntity scan = active.getScan();
        BuildJobEntity job = buildJobRepository.saveAndFlush(new BuildJobEntity(scan));
        scan.changeBuildState(BuildState.pending);
        scan.attachBuildJob(job.getBuildJobId());
        scanIngestRepository.saveAndFlush(scan);
        return new ProcessingStatusResult(floorId, scan.getScanId(), job.getBuildJobId(), "QUEUED", 0.0, null);
    }

    public ProcessingStatusResult processStatus(UUID floorId, Optional<UUID> areaId) {
        floorService.requireFloor(floorId);
        Optional<FloorScanEntity> active = floorService.activeScanForArea(floorId, areaId);
        if (active.isEmpty()) {
            return new ProcessingStatusResult(floorId, null, null, "IDLE", null, null);
        }
        UUID scanId = active.get().getScan().getScanId();
        return buildJobRepository.findFirstByScan_ScanIdOrderByEnqueuedAtDesc(scanId)
                .map(job -> new ProcessingStatusResult(
                        floorId,
                        scanId,
                        job.getBuildJobId(),
                        publicBuildState(job.getState()),
                        job.getProgress(),
                        firstNonBlank(
                                job.getFailureReason() == null ? null : job.getFailureReason().name(),
                                job.getFailureDetail()
                        ).orElse(null)
                ))
                .orElseGet(() -> new ProcessingStatusResult(
                        floorId,
                        scanId,
                        null,
                        publicBuildState(active.get().getScan().getBuildState()),
                        null,
                        null
                ));
    }

    private String publicBuildState(BuildState state) {
        if (state == null || state == BuildState.not_started) {
            return "IDLE";
        }
        if (state == BuildState.pending) {
            return "QUEUED";
        }
        return state.name().toUpperCase();
    }

    private Optional<String> firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return Optional.of(first);
        }
        if (second != null && !second.isBlank()) {
            return Optional.of(second);
        }
        return Optional.empty();
    }
}
