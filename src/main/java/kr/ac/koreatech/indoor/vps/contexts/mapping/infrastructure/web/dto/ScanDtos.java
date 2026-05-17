package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ScanDtos {
    private ScanDtos() {
    }

    public record MergeScansRequest(List<UUID> chunkIds) {
    }

    public record ScanStartRequest(String scanId, String deviceInfo) {
    }

    public record ScanStartResponse(
            UUID scanId,
            UUID floorId,
            UUID areaId,
            String storagePath,
            String state
    ) {
    }

    public record ScanFramesResponse(
            UUID scanId,
            int framesApplied,
            int framesSkipped,
            int linksApplied,
            int linksSkipped,
            int lastNodeId,
            int nodeCount
    ) {
    }

    public record ScanFinalizeResponse(
            UUID scanId,
            UUID floorId,
            String state,
            int nodeCount,
            int keyframeCount,
            int poiMarkCount,
            String payloadSha256
    ) {
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
