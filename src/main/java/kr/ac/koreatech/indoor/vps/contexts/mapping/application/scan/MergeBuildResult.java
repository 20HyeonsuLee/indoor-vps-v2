package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;

public record MergeBuildResult(
        UUID floorId,
        UUID mergedScanId,
        UUID buildJobId,
        String status
) {
}
