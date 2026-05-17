package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;

public record ProcessingStatusResult(
        UUID floorId,
        UUID scanId,
        UUID buildJobId,
        String status,
        Double progress,
        String error
) {
}
