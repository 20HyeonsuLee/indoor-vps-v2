package kr.ac.koreatech.indoor.vps.application.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.api.ClientApiException;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.BuildFloorMapBridgeRequest;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.BuildFloorMapBridgeResponse;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.HealthBridgeResponse;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.LocalizeBridgeResponse;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.application.bridge.BridgeContracts.MergeScanBridgeResponse;
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

    public HealthBridgeResponse health() {
        return call(BridgeCommand.HEALTH, Map.of(), HealthBridgeResponse.class);
    }

    public LocalizeBridgeResponse localize(LocalizeBridgeRequest request) {
        return call(BridgeCommand.LOCALIZE, request, LocalizeBridgeResponse.class);
    }

    public MergeScanBridgeResponse mergeScan(MergeScanBridgeRequest request) {
        return call(BridgeCommand.MERGE_SCAN, request, MergeScanBridgeResponse.class);
    }

    public BuildFloorMapBridgeResponse buildFloorMap(BuildFloorMapBridgeRequest request) {
        return call(BridgeCommand.BUILD_FLOOR_MAP, request, BuildFloorMapBridgeResponse.class);
    }

    public JsonNode callJson(String command, Map<String, Object> payload) {
        return call(command, payload, JsonNode.class);
    }

    public <T> T call(BridgeCommand command, Object payload, Class<T> responseType) {
        return call(command.wireName(), payload, responseType);
    }

    public <T> T call(String command, Object payload, Class<T> responseType) {
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
                throw bridgeError(command, "PYTHON_BRIDGE_TIMEOUT", "Python bridge timed out.", null);
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw bridgeError(command, "PYTHON_BRIDGE_FAILED", stderr.isBlank() ? stdout : stderr, process.exitValue());
            }
            return objectMapper.readValue(stdout, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw bridgeError(command, "PYTHON_BRIDGE_FAILED", e.getMessage(), null);
        }
    }

    private ClientApiException bridgeError(String command, String code, String message, Integer exitCode) {
        return new ClientApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                code,
                message,
                Map.of(
                        "command", command,
                        "exitCode", exitCode == null ? "unknown" : exitCode
                )
        );
    }
}
