package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.FinalizedStreamingScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FinalizeStreamingScanUseCase {

    private final FloorQueryService floorService;
    private final ScanPersistence scanPersistence;
    private final StreamingScanStorage streamingScanStorage;

    public FinalizeStreamingScanUseCase(
            FloorQueryService floorService,
            ScanPersistence scanPersistence,
            StreamingScanStorage streamingScanStorage
    ) {
        this.floorService = floorService;
        this.scanPersistence = scanPersistence;
        this.streamingScanStorage = streamingScanStorage;
    }

    @Transactional
    public ScanFinalizeResult execute(FinalizeStreamingScanCommand command) {
        UUID scanId = command.scanId();
        FinalizedStreamingScan finalized = streamingScanStorage.finalizeScan(scanId, command.manifest(), command.metadata());
        UUID resolvedAreaId = finalized.areaId();
        FloorAreaEntity area = (resolvedAreaId == null
                ? floorService.defaultArea(finalized.floorId())
                : floorService.area(resolvedAreaId))
                .orElseThrow(() -> new kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "AREA_NOT_FOUND", "scan area not found"));
        ScanIngestEntity scan = scanPersistence.findScan(scanId)
                .map(existing -> {
                    existing.replacePayload(finalized.payloadSha256(), finalized.storagePath(), finalized.deviceInfo());
                    return existing;
                })
                .orElseGet(() -> new ScanIngestEntity(
                        scanId,
                        finalized.payloadSha256(),
                        finalized.storagePath(),
                        finalized.deviceInfo(),
                        area.getAreaId()
                ));
        ScanIngestEntity persistedScan = scanPersistence.saveScan(scan);
        String fileName = ScanNaming.streamingScanFileName(scanId);
        scanPersistence.saveFloorScanActive(
                finalized.floorId(), area, persistedScan,
                fileName, finalized.fileSize(), "READY"
        );
        return new ScanFinalizeResult(
                scanId,
                finalized.floorId(),
                "READY",
                finalized.nodeCount(),
                finalized.keyframeCount(),
                finalized.poiMarkCount(),
                finalized.payloadSha256()
        );
    }
}
