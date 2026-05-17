package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.SlamDtos.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.BridgeContracts.LocalizeBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.bridge.PythonBridgeClient;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SlamLocalizationService {
    private final IndoorProperties properties;
    private final PythonBridgeClient bridgeClient;
    private final LocalizationMapProvider mapProvider;

    public SlamLocalizationService(
            IndoorProperties properties,
            PythonBridgeClient bridgeClient,
            LocalizationMapProvider mapProvider
    ) {
        this.properties = properties;
        this.bridgeClient = bridgeClient;
        this.mapProvider = mapProvider;
    }

    public SLAMLocalizeResponse localize(
            List<MultipartFile> images,
            String buildingId,
            String mapId
    ) {
        if (images == null || images.isEmpty()) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "At least one image file is required");
        }
        String resolvedBuildingId = buildingId != null ? buildingId : mapId;
        if (resolvedBuildingId == null || resolvedBuildingId.isBlank()) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "building_id or map_id is required");
        }
        bridgeClient.ensureEnabled();

        List<FloorMapBridgeRef> floorMaps = mapProvider.activeFloorMaps(resolvedBuildingId);
        if (floorMaps.isEmpty()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "MAP_NOT_FOUND", "No maps found for building " + resolvedBuildingId);
        }

        List<Path> tempFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("indoor-localize-");
            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                validateImage(image, i);
                Path file = tempDir.resolve("%02d-%s".formatted(i, safeName(image.getOriginalFilename())));
                image.transferTo(file);
                tempFiles.add(file);
            }
            LocalizeBridgeResponse response = bridgeClient.localize(new LocalizeBridgeRequest(
                    resolvedBuildingId,
                    tempFiles.stream().map(Path::toString).toList(),
                    properties.getStorageRoot().toString(),
                    floorMaps
            ));
            return new SLAMLocalizeResponse(
                    response.pose(),
                    response.confidence(),
                    response.mapId(),
                    response.numMatches(),
                    response.matchedImageIndex(),
                    response.floorId(),
                    response.floorLevel()
            );
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.SERVICE_UNAVAILABLE, "SLAM_LOCALIZE_FAILED", e.getMessage());
        } finally {
            for (Path tempFile : tempFiles) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void validateImage(MultipartFile image, int index) {
        String contentType = image.getContentType();
        boolean accepted = contentType == null
                || contentType.startsWith("image/")
                || contentType.equals("application/octet-stream");
        if (!accepted) {
            throw new ClientApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    "File %d must be an image, got %s".formatted(index + 1, contentType)
            );
        }
        if (image.isEmpty()) {
            throw new ClientApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    "File %d is empty".formatted(index + 1)
            );
        }
    }

    private String safeName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.bin";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
