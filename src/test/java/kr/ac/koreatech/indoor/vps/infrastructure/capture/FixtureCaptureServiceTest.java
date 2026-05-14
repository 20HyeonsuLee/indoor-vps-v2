package kr.ac.koreatech.indoor.vps.infrastructure.capture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.infrastructure.capture.FixtureCaptureService.CaptureRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class FixtureCaptureServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void disabledCaptureDoesNotWriteFiles() {
        Path root = tempDir.resolve("captures");
        FixtureCaptureService service = service(root, false);

        CaptureRecord capture = service.captureScanChunk(
                UUID.randomUUID(),
                new MockMultipartFile("file", "scan.zip", "application/zip", "zip".getBytes(StandardCharsets.UTF_8)),
                null,
                null,
                false
        );

        assertThat(capture.enabled()).isFalse();
        assertThat(root).doesNotExist();
    }

    @Test
    void capturesScanUploadRequestAndResponse() throws Exception {
        FixtureCaptureService service = service(tempDir.resolve("captures"), true);
        UUID floorId = UUID.randomUUID();

        CaptureRecord capture = service.captureScanChunk(
                floorId,
                new MockMultipartFile("file", "phone-scan.zip", "application/zip", "zip".getBytes(StandardCharsets.UTF_8)),
                "scan-1",
                "{\"device\":\"iphone\"}",
                true
        );
        service.writeResponse(capture, Map.of("scanId", "scan-1", "active", true));

        assertThat(capture.directory().resolve("upload-phone-scan.zip")).exists();
        JsonNode request = objectMapper.readTree(capture.directory().resolve("request.json").toFile());
        JsonNode response = objectMapper.readTree(capture.directory().resolve("response.json").toFile());
        assertThat(request.path("type").asText()).isEqualTo("scan-chunk-upload");
        assertThat(request.path("floorId").asText()).isEqualTo(floorId.toString());
        assertThat(request.path("force").asBoolean()).isTrue();
        assertThat(request.path("file").path("sha256").asText()).isNotBlank();
        assertThat(response.path("active").asBoolean()).isTrue();
    }

    @Test
    void capturesLocalizeImagesAndError() throws Exception {
        FixtureCaptureService service = service(tempDir.resolve("captures"), true);

        CaptureRecord capture = service.captureLocalizeImages(List.of(
                new MockMultipartFile("images", "frame one.jpg", "image/jpeg", "jpg".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("images", "frame-two.png", "image/png", "png".getBytes(StandardCharsets.UTF_8))
        ), "building-1", null);
        service.writeError(capture, new ClientApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PYTHON_BRIDGE_DISABLED",
                "python bridge is disabled"
        ));

        assertThat(capture.directory().resolve("images/00-frame_one.jpg")).exists();
        assertThat(capture.directory().resolve("images/01-frame-two.png")).exists();
        JsonNode request = objectMapper.readTree(capture.directory().resolve("request.json").toFile());
        JsonNode error = objectMapper.readTree(capture.directory().resolve("error.json").toFile());
        assertThat(request.path("type").asText()).isEqualTo("slam-localize");
        assertThat(request.path("images").size()).isEqualTo(2);
        assertThat(error.path("code").asText()).isEqualTo("PYTHON_BRIDGE_DISABLED");
    }

    private FixtureCaptureService service(Path root, boolean enabled) {
        IndoorProperties properties = new IndoorProperties();
        properties.getFixtureCapture().setEnabled(enabled);
        properties.getFixtureCapture().setRoot(root);
        return new FixtureCaptureService(properties, objectMapper);
    }
}
