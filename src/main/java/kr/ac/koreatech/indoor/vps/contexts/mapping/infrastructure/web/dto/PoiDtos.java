package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.Map;
import java.util.UUID;

public final class PoiDtos {
    private PoiDtos() {
    }

    public record POIResponse(
            UUID poiId,
            UUID buildingId,
            UUID floorId,
            String name,
            String label,
            String category,
            UUID routeNodeId,
            Map<String, Double> displayPoint,
            boolean needsReview,
            Double llmConfidence
    ) {
    }
}
