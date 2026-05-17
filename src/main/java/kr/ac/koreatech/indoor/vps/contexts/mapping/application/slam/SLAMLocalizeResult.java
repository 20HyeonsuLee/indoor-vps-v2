package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

import java.util.Map;

public record SLAMLocalizeResult(
        Map<String, Object> pose,
        double confidence,
        int numMatches,
        int matchedImageIndex,
        String floorId,
        String areaId,
        int floorLevel
) {
}
