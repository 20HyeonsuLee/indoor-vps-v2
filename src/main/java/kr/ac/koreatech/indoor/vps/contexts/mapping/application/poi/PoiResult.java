package kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi;

import java.util.Map;
import java.util.UUID;

public record PoiResult(
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
