package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface StreamingScanStorage {

    StartedStreamingScan start(UUID floorId, UUID areaId, UUID scanId, Map<String, Object> deviceInfo);

    StreamingFrameStats append(UUID scanId, ScanFramesRequest request);

    FinalizedStreamingScan finalizeScan(UUID scanId, FilePayload manifest, FilePayload metadata);

    record FilePayload(byte[] content, String originalFilename) {
    }

    record ScanFramesRequest(
            List<FramePayload> frames,
            List<FrameLinkPayload> links
    ) {
    }

    record FramePayload(
            int nodeId,
            double stamp,
            String pose,
            String image,
            String calibration,
            Integer mapId,
            Integer weight,
            String depth,
            String scan,
            String scanInfo,
            String label,
            String userData
    ) {
    }

    record FrameLinkPayload(
            int fromId,
            int toId,
            String transform,
            Integer type,
            String informationMatrix,
            String userData
    ) {
    }

    record StartedStreamingScan(UUID scanId, UUID floorId, UUID areaId, String storagePath, String state) {
    }

    record StreamingFrameStats(
            UUID scanId,
            int framesApplied,
            int framesSkipped,
            int linksApplied,
            int linksSkipped,
            int lastNodeId,
            int nodeCount
    ) {
    }

    record FinalizedStreamingScan(
            UUID scanId,
            UUID floorId,
            UUID areaId,
            String storagePath,
            String payloadSha256,
            long fileSize,
            int nodeCount,
            int keyframeCount,
            int poiMarkCount,
            Map<String, Object> deviceInfo
    ) {
    }
}
