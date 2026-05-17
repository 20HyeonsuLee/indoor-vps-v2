package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FixtureCaptureService {
    private static final Logger log = LoggerFactory.getLogger(FixtureCaptureService.class);
    private static final DateTimeFormatter CAPTURE_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final IndoorProperties properties;
    private final CaptureFileIo fileIo;

    public FixtureCaptureService(IndoorProperties properties, CaptureFileIo fileIo) {
        this.properties = properties;
        this.fileIo = fileIo;
    }

    public CaptureRecord captureScanChunk(
            UUID floorId,
            MultipartFile upload,
            String scanId,
            String deviceInfo,
            boolean force
    ) {
        if (!enabled() || upload == null) {
            return CaptureRecord.disabled();
        }
        try {
            Path directory = newCaptureDirectory("scan-chunks", scanId);
            CapturedFile file = fileIo.copyMultipart(upload, directory.resolve("upload"));
            fileIo.writeJson(directory.resolve("request.json"), mapOf(
                    "type", "scan-chunk-upload",
                    "capturedAt", Instant.now().toString(),
                    "floorId", floorId,
                    "scanIdParam", scanId,
                    "deviceInfo", deviceInfo,
                    "force", force,
                    "file", file
            ));
            return new CaptureRecord(true, directory);
        } catch (IOException e) {
            throw captureFailed(e);
        }
    }

    public CaptureRecord captureLocalizeImages(
            List<MultipartFile> images,
            String buildingId,
            String mapId
    ) {
        if (!enabled()) {
            return CaptureRecord.disabled();
        }
        try {
            Path directory = newCaptureDirectory("localize", buildingId != null ? buildingId : mapId);
            Path imageDirectory = directory.resolve("images");
            List<CapturedFile> files = new ArrayList<>();
            if (images != null) {
                for (int i = 0; i < images.size(); i++) {
                    MultipartFile image = images.get(i);
                    if (image == null) {
                        continue;
                    }
                    files.add(fileIo.copyMultipart(image, imageDirectory.resolve("%02d".formatted(i))));
                }
            }
            fileIo.writeJson(directory.resolve("request.json"), mapOf(
                    "type", "slam-localize",
                    "capturedAt", Instant.now().toString(),
                    "buildingId", buildingId,
                    "mapId", mapId,
                    "images", files
            ));
            return new CaptureRecord(true, directory);
        } catch (IOException e) {
            throw captureFailed(e);
        }
    }

    public void writeResponse(CaptureRecord capture, Object response) {
        if (!capture.enabled()) {
            return;
        }
        try {
            fileIo.writeJson(capture.directory().resolve("response.json"), response);
        } catch (IOException e) {
            log.warn("Fixture response capture failed at {}", capture.directory(), e);
        }
    }

    public void writeError(CaptureRecord capture, RuntimeException error) {
        if (!capture.enabled()) {
            return;
        }
        try {
            fileIo.writeJson(capture.directory().resolve("error.json"), errorBody(error));
        } catch (IOException e) {
            log.warn("Fixture error capture failed at {}", capture.directory(), e);
        }
    }

    private boolean enabled() {
        return properties.getFixtureCapture() != null && properties.getFixtureCapture().isEnabled();
    }

    private Path newCaptureDirectory(String type, String hint) throws IOException {
        String captureId = CAPTURE_ID_TIME.format(Instant.now())
                + "-"
                + fileIo.sanitize(hint == null || hint.isBlank() ? "capture" : hint)
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Path directory = properties.getFixtureCapture().getRoot()
                .toAbsolutePath()
                .normalize()
                .resolve(type)
                .resolve(captureId);
        Files.createDirectories(directory);
        return directory;
    }

    private Map<String, Object> errorBody(RuntimeException error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", error.getClass().getName());
        body.put("message", error.getMessage());
        if (error instanceof ClientApiException clientError) {
            body.put("status", clientError.statusCode().value());
            body.put("code", clientError.code());
            body.put("detail", clientError.detail());
        }
        return body;
    }

    private ClientApiException captureFailed(IOException e) {
        return new ClientApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "FIXTURE_CAPTURE_FAILED",
                e.getMessage()
        );
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null) {
                map.put((String) pairs[i], value);
            }
        }
        return map;
    }

    public record CaptureRecord(boolean enabled, Path directory) {
        static CaptureRecord disabled() {
            return new CaptureRecord(false, null);
        }
    }

    public record CapturedFile(
            String path,
            String originalFilename,
            String contentType,
            long size,
            String sha256
    ) {
    }
}
