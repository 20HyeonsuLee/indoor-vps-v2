package kr.ac.koreatech.indoor.vps.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "memory")
public class StorageOnlyLocalizationMapProvider implements LocalizationMapProvider {
    private final IndoorProperties properties;

    public StorageOnlyLocalizationMapProvider(IndoorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<FloorMapBridgeRef> activeFloorMaps(String buildingId) {
        Path singleDb = properties.getStorageRoot().resolve("maps").resolve(buildingId + ".db");
        if (!Files.exists(singleDb)) {
            return List.of();
        }
        return List.of(new FloorMapBridgeRef("", "", 0, singleDb.toString()));
    }
}
