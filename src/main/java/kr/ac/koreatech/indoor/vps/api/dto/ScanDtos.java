package kr.ac.koreatech.indoor.vps.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ScanDtos {
    private ScanDtos() {
    }

    public record MergeScansRequest(List<UUID> chunkIds) {
    }

    public record ScanChunkResponse(
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

    public record MergedScanResponse(UUID floorId, UUID activeScanId, String status) {
    }

    public record ProcessingStatusResponse(
            UUID floorId,
            UUID scanId,
            UUID buildJobId,
            String status,
            Double progress,
            String error
    ) {
    }
}
