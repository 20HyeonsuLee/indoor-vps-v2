package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.localization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class JpaLocalizationMapProvider implements LocalizationMapProvider {
    private final FloorScanRepository floorScanRepository;
    private final IndoorProperties properties;

    public JpaLocalizationMapProvider(
            FloorScanRepository floorScanRepository,
            IndoorProperties properties
    ) {
        this.floorScanRepository = floorScanRepository;
        this.properties = properties;
    }

    @Override
    public List<FloorMapBridgeRef> activeFloorMaps(String buildingId) {
        UUID buildingUuid = parseUuid(buildingId);
        if (buildingUuid == null) {
            return fallbackSingleMap(buildingId);
        }

        List<FloorMapBridgeRef> maps = floorScanRepository.findActiveForBuilding(buildingUuid).stream()
                .map(this::toBridgeRef)
                .toList();
        if (!maps.isEmpty()) {
            return maps;
        }
        return fallbackSingleMap(buildingId);
    }

    private FloorMapBridgeRef toBridgeRef(FloorScanEntity floorScan) {
        return new FloorMapBridgeRef(
                floorScan.getFloor().getFloorId().toString(),
                floorScan.getFloor().getName(),
                floorScan.getFloor().getLevel(),
                rtabmapDbPath(floorScan.getScan().getStoragePath()).toString()
        );
    }

    private List<FloorMapBridgeRef> fallbackSingleMap(String buildingId) {
        Path singleDb = properties.getStorageRoot().resolve("maps").resolve(buildingId + ".db");
        if (!Files.exists(singleDb)) {
            return List.of();
        }
        return List.of(new FloorMapBridgeRef("", "", 0, singleDb.toString()));
    }

    private Path rtabmapDbPath(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null && "rtabmap.db".equals(fileName.toString())) {
            return path;
        }
        if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            return path.getParent().resolve("rtabmap.db");
        }
        return path.resolve("rtabmap.db");
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
