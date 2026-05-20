package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.localization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.LocalizationMapProvider;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorScanEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorScanRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
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
        Optional<UUID> buildingUuid = parseUuid(buildingId);
        if (buildingUuid.isEmpty()) {
            return fallbackSingleMap(buildingId);
        }

        List<FloorMapBridgeRef> maps = floorScanRepository.findActiveForBuilding(buildingUuid.get()).stream()
                .map(this::toBridgeRef)
                .toList();
        if (!maps.isEmpty()) {
            return maps;
        }
        return fallbackSingleMap(buildingId);
    }

    @Override
    public List<FloorMapBridgeRef> activeFloorMapsV2(String buildingId) {
        Optional<UUID> buildingUuid = parseUuid(buildingId);
        if (buildingUuid.isEmpty()) {
            return List.of();
        }
        return floorScanRepository.findActiveForBuilding(buildingUuid.get()).stream()
                .map(this::toBridgeRefV2)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private FloorMapBridgeRef toBridgeRef(FloorScanEntity floorScan) {
        return new FloorMapBridgeRef(
                floorScan.getFloor().getFloorId().toString(),
                floorScan.getArea().getAreaId().toString(),
                floorScan.getFloor().getName(),
                floorScan.getFloor().getLevel(),
                rtabmapDbPath(floorScan.getScan().getStoragePath()).toString()
        );
    }

    private FloorMapBridgeRef toBridgeRefV2(FloorScanEntity floorScan) {
        Path v2Db = v2DbPath(floorScan.getScan().getStoragePath());
        if (v2Db == null || !Files.exists(v2Db)) {
            return null;
        }
        return new FloorMapBridgeRef(
                floorScan.getFloor().getFloorId().toString(),
                floorScan.getArea().getAreaId().toString(),
                floorScan.getFloor().getName(),
                floorScan.getFloor().getLevel(),
                v2Db.toString()
        );
    }

    /** V2(rtabmap-native) 아티팩트는 {@code <scan_dir>/v2/} 하위에 격리 보관. */
    private Path v2DbPath(String storagePath) {
        Path scanDir = scanDirOf(storagePath);
        if (scanDir == null) {
            return null;
        }
        Path v2Dir = scanDir.resolve("v2");
        Path reprocessed = v2Dir.resolve("rtabmap_reprocessed.db");
        if (Files.exists(reprocessed)) {
            return reprocessed;
        }
        Path raw = v2Dir.resolve("rtabmap.db");
        return Files.exists(raw) ? raw : null;
    }

    private Path scanDirOf(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        if (fileName != null) {
            String n = fileName.toString();
            if (n.endsWith(".db") || n.endsWith(".zip")) {
                return path.getParent();
            }
        }
        return path;
    }

    private List<FloorMapBridgeRef> fallbackSingleMap(String buildingId) {
        Path singleDb = properties.getStorageRoot().resolve("maps").resolve(buildingId + ".db");
        if (!Files.exists(singleDb)) {
            return List.of();
        }
        return List.of(new FloorMapBridgeRef("", "", "", 0, singleDb.toString()));
    }

    private Path rtabmapDbPath(String storagePath) {
        Path path = Path.of(storagePath);
        if (!path.isAbsolute()) {
            path = properties.getStorageRoot().resolve(path);
        }
        Path fileName = path.getFileName();
        Path scanDir;
        if (fileName != null && fileName.toString().endsWith(".db")) {
            scanDir = path.getParent();
        } else if (fileName != null && fileName.toString().endsWith(".zip") && path.getParent() != null) {
            scanDir = path.getParent();
        } else {
            scanDir = path;
        }
        Path reprocessed = scanDir.resolve("rtabmap_reprocessed.db");
        if (Files.exists(reprocessed)) {
            return reprocessed;
        }
        return scanDir.resolve("rtabmap.db");
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
