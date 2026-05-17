package kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor;

import java.time.Instant;
import java.util.UUID;

public record FloorResult(
        UUID floorId,
        UUID buildingId,
        String name,
        int level,
        Double height,
        boolean hasPath,
        boolean hasPly,
        UUID activeScanId,
        Instant createdAt,
        Instant updatedAt
) {
}
