package kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan;

import java.util.Optional;
import java.util.UUID;

public record StartStreamingScanCommand(
        UUID floorId,
        String scanIdText,
        String deviceInfo,
        Optional<UUID> areaId
) {
    public StartStreamingScanCommand(UUID floorId, String scanIdText, String deviceInfo) {
        this(floorId, scanIdText, deviceInfo, Optional.empty());
    }
}
