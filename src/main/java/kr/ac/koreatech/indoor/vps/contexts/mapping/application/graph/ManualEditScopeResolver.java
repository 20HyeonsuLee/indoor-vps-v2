package kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.ScanIngestEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ManualEditScopeResolver {

    private final FloorAreaRepository areaRepository;
    private final FloorScanRepository floorScanRepository;

    public ManualEditScopeResolver(
            FloorAreaRepository areaRepository,
            FloorScanRepository floorScanRepository
    ) {
        this.areaRepository = areaRepository;
        this.floorScanRepository = floorScanRepository;
    }

    public ManualEditScope resolve(UUID areaId) {
        FloorAreaEntity area = areaRepository.findById(areaId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId));
        FloorScanEntity floorScan = floorScanRepository
                .findFirstByArea_AreaIdAndActiveTrueOrderByCreatedAtDesc(areaId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.CONFLICT, "NO_ACTIVE_SCAN",
                        "area has no active scan; cannot create manual edits"));
        ScanIngestEntity scan = floorScan.getScan();
        UUID buildJobId = scan.getBuildJobId();
        if (buildJobId == null) {
            throw new ClientApiException(
                    HttpStatus.CONFLICT, "SCAN_NOT_BUILT",
                    "active scan has not been processed yet; cannot create manual edits");
        }
        return new ManualEditScope(area, scan.getScanId(), buildJobId);
    }
}
