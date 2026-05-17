package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.StreamingFrameStats;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ScanDtos.ScanFramesRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PushStreamingFramesUseCase {

    private final StreamingScanStorage streamingScanStorage;

    public PushStreamingFramesUseCase(StreamingScanStorage streamingScanStorage) {
        this.streamingScanStorage = streamingScanStorage;
    }

    public StreamingFrameStats execute(UUID scanId, ScanFramesRequest request) {
        return streamingScanStorage.append(scanId, request);
    }
}
