package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.Map;

public final class SlamDtos {
    private SlamDtos() {
    }

    public record SLAMLocalizeResponse(
            Map<String, Object> pose,
            double confidence,
            String mapId,
            int numMatches,
            int matchedImageIndex,
            String floorId,
            int floorLevel
    ) {
    }
}
