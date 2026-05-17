package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.List;
import java.util.UUID;

public final class VerticalPassageResult {
    private VerticalPassageResult() {
    }

    public record Segment(
            String stopId,
            String levelId,
            String routeNodeId,
            Double x,
            Double y,
            String floorId,
            String kind
    ) {
    }

    public record Summary(
            UUID passageId,
            UUID buildingId,
            String connectorType,
            String connectorKey,
            String name,
            boolean mock,
            List<Segment> segments
    ) {
    }
}
