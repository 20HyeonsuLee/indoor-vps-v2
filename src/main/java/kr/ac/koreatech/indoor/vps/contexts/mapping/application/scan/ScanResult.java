package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.time.Instant;
import java.util.UUID;

public final class ScanResult {
    private ScanResult() {
    }

    public record ScanChunk(
            UUID chunkId,
            UUID floorId,
            UUID scanId,
            String fileName,
            Long fileSize,
            String status,
            boolean active,
            int uploadOrder,
            Instant createdAt
    ) {
    }

    public record ScanFinalized(
            UUID scanId,
            UUID floorId,
            String state,
            int nodeCount,
            int keyframeCount,
            int poiMarkCount,
            String payloadSha256
    ) {
    }

    public record ScanMerged(UUID floorId, UUID activeScanId, String status) {
    }

    public record ProcessingStatus(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            String status,
            Double progress,
            String error
    ) {
    }
}
