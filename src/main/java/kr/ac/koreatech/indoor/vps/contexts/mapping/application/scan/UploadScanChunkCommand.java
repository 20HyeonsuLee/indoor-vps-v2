package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.Optional;
import java.util.UUID;

public record UploadScanChunkCommand(
        UUID floorId,
        byte[] fileContent,
        String originalFilename,
        String scanIdText,
        String deviceInfo,
        boolean force,
        Optional<UUID> areaId
) {
    public UploadScanChunkCommand(
            UUID floorId, byte[] fileContent, String originalFilename,
            String scanIdText, String deviceInfo, boolean force) {
        this(floorId, fileContent, originalFilename, scanIdText, deviceInfo, force, Optional.empty());
    }
}
