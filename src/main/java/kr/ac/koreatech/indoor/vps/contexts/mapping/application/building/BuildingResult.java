package kr.ac.koreatech.indoor.vps.contexts.mapping.application.building;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.VerticalPassageResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.building.BuildingStatus;

public final class BuildingResult {
    private BuildingResult() {
    }

    public record Summary(
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

    public record Detail(
            UUID buildingId,
            String name,
            String description,
            Double latitude,
            Double longitude,
            BuildingStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<FloorResult> floors,
            List<VerticalPassageResult.Summary> verticalPassages
    ) {
    }
}
