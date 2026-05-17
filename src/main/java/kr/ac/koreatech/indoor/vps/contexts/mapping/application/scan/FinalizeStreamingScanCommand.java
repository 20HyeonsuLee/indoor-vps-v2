package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.StreamingScanStorage.FilePayload;

public record FinalizeStreamingScanCommand(UUID scanId, FilePayload manifest, FilePayload metadata) {
}
