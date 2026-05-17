package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.UUID;

public record UploadScanChunkCommand(
        UUID floorId,
        byte[] fileContent,
        String originalFilename,
        String scanIdText,
        String deviceInfo,
        boolean force
) {
}
