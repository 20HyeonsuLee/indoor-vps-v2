package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.util.Map;

public final class SlamDtos {
    private SlamDtos() {
    }

    public record SLAMLocalizeResponse(
            Map<String, Object> pose,
            double confidence,
            int numMatches,
            int matchedImageIndex,
            String methodUsed,
            String floorId,
            String areaId,
            int floorLevel
    ) {
    }
}
