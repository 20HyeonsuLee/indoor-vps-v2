package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

import java.util.Map;

public record SLAMLocalizeResult(
        Map<String, Object> pose,
        double confidence,
        String mapId,
        int numMatches,
        int matchedImageIndex,
        String floorId,
        int floorLevel
) {
}
