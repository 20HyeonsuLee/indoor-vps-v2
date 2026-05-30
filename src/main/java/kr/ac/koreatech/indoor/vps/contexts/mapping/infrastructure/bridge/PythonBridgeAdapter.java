package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.bridge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.BuildSuperpointIndexResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.ExportPointcloudResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.HealthBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeRequest;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.MergeScanBridgeResponse;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.PythonBridge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PythonBridgeAdapter implements PythonBridge {
    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    public PythonBridgeAdapter(IndoorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void ensureEnabled() {
        if (!properties.getPython().isEnabled()) {
            throw new ClientApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PYTHON_BRIDGE_DISABLED",
                    "Python bridge is disabled for this Java runtime."
            );
        }
    }

    @Override
    public HealthBridgeResponse health() {
        return call(BridgeCommand.HEALTH, Map.of(), HealthBridgeResponse.class);
    }

    @Override
    public LocalizeBridgeResponse localize(LocalizeBridgeRequest request) {
        return call(BridgeCommand.LOCALIZE, request, LocalizeBridgeResponse.class);
    }

    @Override
    public MergeScanBridgeResponse mergeScan(MergeScanBridgeRequest request) {
        return call(BridgeCommand.MERGE_SCAN, request, MergeScanBridgeResponse.class);
    }

    @Override
    public BuildSuperpointIndexResponse buildSuperpointIndex(BuildSuperpointIndexRequest request) {
        return call(BridgeCommand.BUILD_SUPERPOINT_INDEX, request, BuildSuperpointIndexResponse.class);
    }

    @Override
    public ExportPointcloudResponse exportPointcloud(ExportPointcloudRequest request) {
        return call(BridgeCommand.EXPORT_POINTCLOUD, request, ExportPointcloudResponse.class);
    }

    private <T> T call(BridgeCommand command, Object payload, Class<T> responseType) {
        return call(command.wireName(), payload, responseType);
    }

    private <T> T call(String command, Object payload, Class<T> responseType) {
        ensureEnabled();

        try {
            String input = objectMapper.writeValueAsString(payload);
            ProcessBuilder processBuilder = new ProcessBuilder(List.of(
                    properties.getPython().getExecutable(),
                    properties.getPython().getBridgeScript().toString(),
                    command
            )).redirectErrorStream(false);
            applyEnvironment(processBuilder);
            Process process = processBuilder.start();

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
                throw bridgeErrorFromProcess(command, stdout, stderr, process.exitValue());
            }
            return objectMapper.readValue(stdout, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw bridgeError(command, "PYTHON_BRIDGE_FAILED", e.getMessage(), null);
        }
    }

    private void applyEnvironment(ProcessBuilder processBuilder) {
        Map<String, String> environment = processBuilder.environment();
        environment.put("STORAGE_ROOT", properties.getStorageRoot().toAbsolutePath().normalize().toString());
        String backendSource = properties.getPython().getBackendSource();
        if (backendSource != null && !backendSource.isBlank()) {
            environment.put("INDOOR_LEGACY_BACKEND_SRC", backendSource);
        }
        String device = properties.getPython().getDevice();
        if (device != null && !device.isBlank()) {
            String normalizedDevice = device.trim();
            environment.put("INDOOR_ML_DEVICE", normalizedDevice);
            if ("cpu".equalsIgnoreCase(normalizedDevice)) {
                environment.put("CUDA_VISIBLE_DEVICES", "");
            }
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

    private ClientApiException bridgeErrorFromProcess(String command, String stdout, String stderr, int exitCode) {
        String body = stderr.isBlank() ? stdout : stderr;
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            if (!error.isObject() || error.path("code").asText().isBlank()) {
                return bridgeError(command, "PYTHON_BRIDGE_FAILED", body, exitCode);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            if (error.path("detail").isObject()) {
                detail.putAll(objectMapper.convertValue(error.path("detail"), new TypeReference<>() {
                }));
            }
            detail.put("command", command);
            detail.put("exitCode", exitCode);
            return new ClientApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    error.path("code").asText(),
                    error.path("message").asText("Python bridge failed."),
                    detail
            );
        } catch (IOException e) {
            return bridgeError(command, "PYTHON_BRIDGE_FAILED", body, exitCode);
        }
    }
}
