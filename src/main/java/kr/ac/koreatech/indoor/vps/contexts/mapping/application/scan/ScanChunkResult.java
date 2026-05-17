package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.time.Instant;
import java.util.UUID;

public record ScanChunkResult(
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
