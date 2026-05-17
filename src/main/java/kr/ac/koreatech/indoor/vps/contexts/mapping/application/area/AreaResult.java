package kr.ac.koreatech.indoor.vps.contexts.mapping.application.area;

import java.time.Instant;
import java.util.UUID;

public record AreaResult(
        UUID areaId,
        UUID floorId,
        int areaIndex,
        String label,
        boolean isDefault,
        Instant createdAt
) {
}
