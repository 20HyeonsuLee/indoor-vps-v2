package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private final ObjectMapper objectMapper;

    public FixtureCaptureService(IndoorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
            CapturedFile file = copyMultipart(upload, directory.resolve("upload"));
            writeJson(directory.resolve("request.json"), mapOf(
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
                    files.add(copyMultipart(image, imageDirectory.resolve("%02d".formatted(i))));
                }
            }
            writeJson(directory.resolve("request.json"), mapOf(
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
            writeJson(capture.directory().resolve("response.json"), response);
        } catch (IOException e) {
            log.warn("Fixture response capture failed at {}", capture.directory(), e);
        }
    }

    public void writeError(CaptureRecord capture, RuntimeException error) {
        if (!capture.enabled()) {
            return;
        }
        try {
            writeJson(capture.directory().resolve("error.json"), errorBody(error));
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
                + sanitize(hint == null || hint.isBlank() ? "capture" : hint)
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

    private CapturedFile copyMultipart(MultipartFile file, Path targetPrefix) throws IOException {
        Files.createDirectories(targetPrefix.getParent());
        String filename = safeFileName(file.getOriginalFilename());
        Path target = targetPrefix.resolveSibling(targetPrefix.getFileName() + "-" + filename);
        MessageDigest digest = sha256();
        long size = 0;
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return new CapturedFile(
                target.getFileName().toString(),
                file.getOriginalFilename(),
                file.getContentType(),
                size,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
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

    private String safeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return sanitize(filename);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
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
