package kr.ac.koreatech.indoor.vps.application;

import static kr.ac.koreatech.indoor.vps.api.dto.ApiDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.application.bridge.PythonBridgeClient;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SlamLocalizationService {
    private final IndoorProperties properties;
    private final PythonBridgeClient bridgeClient;
    private final ObjectMapper objectMapper;

    public SlamLocalizationService(
            IndoorProperties properties,
            PythonBridgeClient bridgeClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.bridgeClient = bridgeClient;
        this.objectMapper = objectMapper;
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

        List<Path> tempFiles = new ArrayList<>();
        try {
            Path tempDir = Files.createTempDirectory("indoor-localize-");
            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                validateImage(image, i);
                Path file = tempDir.resolve("%02d-%s".formatted(i, safeName(image.getOriginalFilename())));
                image.transferTo(file);
                tempFiles.add(file);
            }
            JsonNode response = bridgeClient.callJson("localize", Map.of(
                    "buildingId", resolvedBuildingId,
                    "imagePaths", tempFiles.stream().map(Path::toString).toList(),
                    "storageRoot", properties.getStorageRoot().toString()
            ));
            return objectMapper.treeToValue(response, SLAMLocalizeResponse.class);
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.SERVICE_UNAVAILABLE, "SLAM_LOCALIZE_FAILED", e.getMessage());
        } finally {
            for (Path tempFile : tempFiles) {
                try {
                    Files.deleteIfExists(tempFile);
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
