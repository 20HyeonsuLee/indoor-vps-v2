package kr.ac.koreatech.indoor.vps.application.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PythonBridgeClient {
    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    public PythonBridgeClient(IndoorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode callJson(String command, Map<String, Object> payload) {
        if (!properties.getPython().isEnabled()) {
            throw new ClientApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PYTHON_BRIDGE_DISABLED",
                    "Python bridge is disabled for this Java runtime."
            );
        }

        try {
            String input = objectMapper.writeValueAsString(payload);
            Process process = new ProcessBuilder(List.of(
                    properties.getPython().getExecutable(),
                    properties.getPython().getBridgeScript().toString(),
                    command
            )).redirectErrorStream(false).start();

            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            boolean finished = process.waitFor(
                    Duration.ofSeconds(properties.getPython().getTimeoutSeconds())
                            .toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                throw bridgeError("PYTHON_BRIDGE_TIMEOUT", "Python bridge timed out.");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw bridgeError("PYTHON_BRIDGE_FAILED", stderr.isBlank() ? stdout : stderr);
            }
            return objectMapper.readTree(stdout);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw bridgeError("PYTHON_BRIDGE_FAILED", e.getMessage());
        }
    }

    private ClientApiException bridgeError(String code, String message) {
        return new ClientApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
