package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

import java.util.List;

public record LocalizeCommand(
        List<ImagePayload> images,
        List<DepthPayload> depths,   // optional, parallel to images; null entries allowed
        String buildingId,
        String mapId,
        String floorId
) {
    public LocalizeCommand(List<ImagePayload> images, String buildingId, String mapId) {
        this(images, List.of(), buildingId, mapId, null);
    }

    public LocalizeCommand(List<ImagePayload> images, String buildingId, String mapId, String floorId) {
        this(images, List.of(), buildingId, mapId, floorId);
    }

    public record ImagePayload(byte[] content, String originalFilename, String contentType) {
    }

    public record DepthPayload(byte[] content, String originalFilename) {
    }
}
