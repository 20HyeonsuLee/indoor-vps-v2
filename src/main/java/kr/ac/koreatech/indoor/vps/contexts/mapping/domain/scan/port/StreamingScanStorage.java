package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port;

import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.ScanFramesRequest;
import org.springframework.web.multipart.MultipartFile;

public interface StreamingScanStorage {

    StartedStreamingScan start(UUID floorId, UUID scanId, Map<String, Object> deviceInfo);

    StreamingFrameStats append(UUID scanId, ScanFramesRequest request);

    FinalizedStreamingScan finalizeScan(UUID scanId, MultipartFile manifest, MultipartFile metadata);

    record StartedStreamingScan(UUID scanId, UUID floorId, String storagePath, String state) {
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
