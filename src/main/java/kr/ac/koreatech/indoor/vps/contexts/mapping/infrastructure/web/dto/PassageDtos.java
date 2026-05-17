package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

public final class PassageDtos {
    private PassageDtos() {
    }

    public record PassageSegment(
            String stopId,
            String levelId,
            String routeNodeId,
            Double x,
            Double y,
            String floorId,
            String kind
    ) {
    }

    public record VerticalPassageResponse(
            UUID passageId,
            UUID buildingId,
            String connectorType,
            String connectorKey,
            String name,
            boolean mock,
            List<PassageSegment> segments
    ) {
    }
}
