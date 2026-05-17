package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;

public record ScanMergeRunCommand(
        UUID floorId,
        UUID mergedScanId,
        List<FloorScanEntity> sources
) {
}
