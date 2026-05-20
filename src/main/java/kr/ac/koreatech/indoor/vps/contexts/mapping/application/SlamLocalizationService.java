package kr.ac.koreatech.indoor.vps.contexts.mapping.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.LocalizeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.LocalizeCommand.ImagePayload;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SLAMLocalizeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SlamLocalizer;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * SuperPoint+LightGlue 기반 측위. Bean 등록은 {@link kr.ac.koreatech.indoor.vps.config.SlamLocalizerConfig}
 * 에서 primary/shadow 각각 한 instance씩 명시. variant=SHADOW면 같은 buildingId에 대해
 * {@link LocalizationMapProvider#activeFloorMapsV2}로 rtabmap-native artefact (<scan_dir>/v2/)를
 * 가리키는 FloorMapBridgeRef를 사용. 그 외 로직은 두 variant 공통.
 */
public class SlamLocalizationService implements SlamLocalizer {
    public enum Variant { PRIMARY, SHADOW }

    private final IndoorProperties properties;
    private final PythonBridge bridge;
    private final LocalizationMapProvider mapProvider;
    private final Variant variant;

    public SlamLocalizationService(
            IndoorProperties properties,
            PythonBridge bridge,
            LocalizationMapProvider mapProvider,
            Variant variant
    ) {
        this.properties = properties;
        this.bridge = bridge;
        this.mapProvider = mapProvider;
        this.variant = variant;
    }

    @Override
    @Transactional(readOnly = true)
    public SLAMLocalizeResult localize(LocalizeCommand command) {
        if (command.images() == null || command.images().isEmpty()) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "At least one image file is required");
        }
        String resolvedBuildingId = command.buildingId() != null ? command.buildingId() : command.mapId();
        if (resolvedBuildingId == null || resolvedBuildingId.isBlank()) {
            throw new ClientApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "building_id or map_id is required");
        }
        bridge.ensureEnabled();

        List<FloorMapBridgeRef> floorMaps = variant == Variant.SHADOW
                ? mapProvider.activeFloorMapsV2(resolvedBuildingId)
                : mapProvider.activeFloorMaps(resolvedBuildingId);
        if (floorMaps.isEmpty()) {
            throw new ClientApiException(HttpStatus.NOT_FOUND, "MAP_NOT_FOUND",
                    "No maps found for building " + resolvedBuildingId + " (variant=" + variant + ")");
        }
        if (command.floorId() != null && !command.floorId().isBlank()) {
            floorMaps = floorMaps.stream()
                    .filter(ref -> command.floorId().equalsIgnoreCase(ref.floorId()))
                    .toList();
            if (floorMaps.isEmpty()) {
                throw new ClientApiException(HttpStatus.NOT_FOUND, "FLOOR_MAP_NOT_FOUND",
                        "No active map for floor " + command.floorId());
            }
        }

        List<Path> tempFiles = new ArrayList<>();
        List<Path> tempDepthFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("indoor-localize-");
            for (int i = 0; i < command.images().size(); i++) {
                ImagePayload image = command.images().get(i);
                validateImage(image, i);
                Path file = tempDir.resolve("%02d-%s".formatted(i, safeName(image.originalFilename())));
                // SHADOW variant: 클라가 보내는 query는 항상 landscape인데 v2(rtabmap iOS native)
                // keyframe은 portrait. SuperPoint 매칭이 0에 가깝게 떨어지므로 query를 90° CW
                // 회전해 portrait로 맞춤. PRIMARY는 절대 손대지 않음 (회귀 위험 0).
                byte[] payload = variant == Variant.SHADOW
                        ? ImageRotation.ensurePortrait90Cw(image.content(), "jpg")
                        : image.content();
                Files.write(file, payload);
                tempFiles.add(file);
            }
            List<String> depthPaths = new ArrayList<>();
            List<LocalizeCommand.DepthPayload> depths = command.depths() == null
                    ? List.of() : command.depths();
            for (int i = 0; i < command.images().size(); i++) {
                LocalizeCommand.DepthPayload d = i < depths.size() ? depths.get(i) : null;
                if (d == null || d.content() == null || d.content().length == 0) {
                    depthPaths.add("");
                    continue;
                }
                Path df = tempDir.resolve("%02d-depth-%s".formatted(i, safeName(d.originalFilename())));
                byte[] dPayload = variant == Variant.SHADOW
                        ? ImageRotation.ensurePortrait90Cw(d.content(), "png")
                        : d.content();
                Files.write(df, dPayload);
                tempDepthFiles.add(df);
                depthPaths.add(df.toString());
            }
            LocalizeBridgeResponse response = bridge.localize(new LocalizeBridgeRequest(
                    resolvedBuildingId,
                    tempFiles.stream().map(Path::toString).toList(),
                    depthPaths,
                    properties.getStorageRoot().toString(),
                    floorMaps
            ));
            return new SLAMLocalizeResult(
                    response.pose(),
                    response.confidence(),
                    response.numMatches(),
                    response.matchedImageIndex(),
                    response.methodUsed(),
                    response.floorId(),
                    response.areaId(),
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
            for (Path tempFile : tempDepthFiles) {
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

    private void validateImage(ImagePayload image, int index) {
        String contentType = image.contentType();
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
        if (image.content() == null || image.content().length == 0) {
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
