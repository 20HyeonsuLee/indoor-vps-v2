package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;

public record ScanFinalizeResult(
        UUID scanId,
        UUID floorId,
        String state,
        int nodeCount,
        int keyframeCount,
        int poiMarkCount,
        String payloadSha256
) {
}
