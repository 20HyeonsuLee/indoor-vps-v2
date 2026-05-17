package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.ScanIngestRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.FinalizedStreamingScan;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.ScanFinalizeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class FinalizeStreamingScanUseCase {

    private final FloorUseCase floorService;
    private final ScanIngestRepository scanIngestRepository;
    private final FloorScanRepository floorScanRepository;
    private final StreamingScanStorage streamingScanStorage;

    public FinalizeStreamingScanUseCase(
            FloorUseCase floorService,
            ScanIngestRepository scanIngestRepository,
            FloorScanRepository floorScanRepository,
            StreamingScanStorage streamingScanStorage
    ) {
        this.floorService = floorService;
        this.scanIngestRepository = scanIngestRepository;
        this.floorScanRepository = floorScanRepository;
        this.streamingScanStorage = streamingScanStorage;
    }

    @Transactional
    public ScanFinalizeResponse execute(UUID scanId, MultipartFile manifest, MultipartFile metadata) {
        FinalizedStreamingScan finalized = streamingScanStorage.finalizeScan(scanId, manifest, metadata);
        FloorEntity floor = floorService.requireFloor(finalized.floorId());
        ScanIngestEntity scan = scanIngestRepository.findById(scanId)
                .map(existing -> {
                    existing.replacePayload(finalized.payloadSha256(), finalized.storagePath(), finalized.deviceInfo());
                    return existing;
                })
                .orElseGet(() -> new ScanIngestEntity(
                        scanId,
                        finalized.payloadSha256(),
                        finalized.storagePath(),
                        finalized.deviceInfo()
                ));
        ScanIngestEntity persistedScan = scanIngestRepository.saveAndFlush(scan);

        floorScanRepository.deactivateForFloor(finalized.floorId());
        floorScanRepository.flush();
        String fileName = ScanNaming.streamingScanFileName(scanId);
        FloorScanEntity floorScan = floorScanRepository
                .findByFloor_FloorIdAndScan_ScanId(finalized.floorId(), scanId)
                .orElseGet(() -> new FloorScanEntity(
                        floor,
                        persistedScan,
                        fileName,
                        finalized.fileSize(),
                        floorScanRepository.nextUploadOrder(finalized.floorId())
                ));
        floorScan.updateStoredFile(fileName, finalized.fileSize(), "READY");
        floorScan.changeActive(true);
        floorScanRepository.saveAndFlush(floorScan);
        return new ScanFinalizeResponse(
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
