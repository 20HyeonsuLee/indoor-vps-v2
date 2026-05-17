package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

import java.util.List;

public record LocalizeCommand(
        List<ImagePayload> images,
        String buildingId,
        String mapId
) {
    public record ImagePayload(byte[] content, String originalFilename, String contentType) {
    }
}
