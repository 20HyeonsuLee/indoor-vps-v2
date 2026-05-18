package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

import java.util.List;

public record LocalizeCommand(
        List<ImagePayload> images,
        String buildingId,
        String mapId,
        String floorId
) {
    public LocalizeCommand(List<ImagePayload> images, String buildingId, String mapId) {
        this(images, buildingId, mapId, null);
    }

    public record ImagePayload(byte[] content, String originalFilename, String contentType) {
    }
}
