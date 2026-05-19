package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.FloorDtos.FloorResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.PassageDtos.VerticalPassageResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.building.BuildingStatus;

public final class BuildingDtos {
    private BuildingDtos() {
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

    public record NodeImagesRequest(
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double z
    ) {
    }
}
