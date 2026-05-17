package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class FloorDtos {
    private FloorDtos() {
    }

    public record FloorCreateRequest(@NotBlank String name, int level, Double height) {
    }

    public record FloorUpdateRequest(String name, Double height) {
    }

    public record FloorResponse(
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
}
