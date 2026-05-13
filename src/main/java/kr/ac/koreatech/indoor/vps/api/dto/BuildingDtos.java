package kr.ac.koreatech.indoor.vps.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.dto.FloorDtos.FloorResponse;
import kr.ac.koreatech.indoor.vps.api.dto.PassageDtos.VerticalPassageResponse;

public final class BuildingDtos {
    private BuildingDtos() {
    }

    public enum BuildingStatus {
        DRAFT,
        ACTIVE
    }

    public record BuildingCreateRequest(
            @NotBlank String name,
            String description,
            Double latitude,
            Double longitude
    ) {
    }

    public record BuildingUpdateRequest(
            String name,
            String description,
            Double latitude,
            Double longitude
    ) {
    }

    public record BuildingStatusRequest(@NotNull BuildingStatus status) {
    }

    public record BuildingResponse(
            UUID buildingId,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record BuildingDetailResponse(
            UUID buildingId,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<FloorResponse> floors,
            List<VerticalPassageResponse> verticalPassages
    ) {
    }
}
