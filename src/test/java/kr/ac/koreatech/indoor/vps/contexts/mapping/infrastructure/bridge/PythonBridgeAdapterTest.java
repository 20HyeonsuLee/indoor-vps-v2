package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.FloorMapBridgeRef;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.LocalizeBridgeRequest;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.junit.jupiter.api.Test;

class PythonBridgeAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void healthReturnsSupportedCommands() throws Exception {
        BridgeResult result = call("health", Map.of());

        assertThat(result.exitCode()).isZero();
        JsonNode stdout = objectMapper.readTree(result.stdout());
        assertThat(stdout.path("ok").asBoolean()).isTrue();
        assertThat(stdout.path("commands").valueStream().map(JsonNode::asText).toList())
                .containsExactly("health", "localize", "merge_scan", "build_superpoint_index");
        assertThat(stdout.path("mlDevice").asText()).isEqualTo("cpu");
    }

    @Test
    void unknownCommandReturnsTypedError() throws Exception {
        BridgeResult result = call("unknown", Map.of());

        assertThat(result.exitCode()).isEqualTo(2);
        JsonNode error = objectMapper.readTree(result.stderr()).path("error");
        assertThat(error.path("code").asText()).isEqualTo("BRIDGE_UNKNOWN_COMMAND");
    }

    @Test
    void localizeValidatesSchemaBeforeImplementation() throws Exception {
        BridgeResult missing = call("localize", Map.of("buildingId", "b1"));
        assertThat(missing.exitCode()).isEqualTo(2);
        assertThat(objectMapper.readTree(missing.stderr()).path("error").path("code").asText())
                .isEqualTo("BRIDGE_VALIDATION_ERROR");

        BridgeResult contractOnly = call("localize", Map.of(
                "buildingId", "b1",
                "storageRoot", "/tmp/storage",
                "imagePaths", List.of("/tmp/a.jpg"),
                "floorMaps", List.of(Map.of(
                        "floorId", "f1",
                        "floorName", "1F",
                        "level", 1,
                        "filePath", "/tmp/rtabmap.db"
                )),
                "contractOnly", true
        ));
        assertThat(contractOnly.exitCode()).isZero();
    }

    @Test
    void mergeCommandValidatesSchema() throws Exception {
        BridgeResult merge = call("merge_scan", Map.of(
                "floorId", "f1",
                "scanId", "s1",
                "sourcePaths", List.of("/tmp/a.db"),
                "outputDir", "/tmp/out",
                "contractOnly", true
        ));

        assertThat(merge.exitCode()).isZero();
    }

    @Test
    void buildSuperpointIndexValidatesSchema() throws Exception {
        BridgeResult missing = call("build_superpoint_index", Map.of("scanId", "s1"));
        assertThat(missing.exitCode()).isEqualTo(2);
        assertThat(objectMapper.readTree(missing.stderr()).path("error").path("code").asText())
                .isEqualTo("BRIDGE_VALIDATION_ERROR");

        BridgeResult contractOnly = call("build_superpoint_index", Map.of(
                "scanId", "s1",
                "dbPath", "/tmp/rtabmap.db",
                "contractOnly", true
        ));
        assertThat(contractOnly.exitCode()).isZero();
    }

    @Test
    void javaBridgeAdapterPreservesTypedPythonErrors() {
        IndoorProperties properties = new IndoorProperties();
        properties.getPython().setEnabled(true);
        properties.getPython().setExecutable("python3");
        properties.getPython().setBridgeScript(Path.of("scripts/python_bridge/bridge_entry.py"));
        properties.getPython().setBackendSource("/tmp/indoor-vps-v2-missing-python-src");
        PythonBridgeAdapter adapter = new PythonBridgeAdapter(properties, objectMapper);

        assertThatThrownBy(() -> adapter.localize(new LocalizeBridgeRequest(
                "building-1",
                List.of("/tmp/frame.jpg"),
                "/tmp/storage",
                List.of(new FloorMapBridgeRef("floor-1", "area-1", "1F", 1, "/tmp/rtabmap.db"))
        )))
                .isInstanceOfSatisfying(ClientApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("BRIDGE_BACKEND_NOT_CONFIGURED");
                    assertThat(error.detail()).containsEntry("command", "localize");
                    assertThat(error.detail()).containsEntry("exitCode", 2);
                    assertThat(error.detail().get("path").toString())
                            .endsWith("/tmp/indoor-vps-v2-missing-python-src");
                });
    }

    @Test
    void javaBridgeAdapterForcesConfiguredMlDevice() {
        IndoorProperties properties = new IndoorProperties();
        properties.getPython().setEnabled(true);
        properties.getPython().setExecutable("python3");
        properties.getPython().setBridgeScript(Path.of("scripts/python_bridge/bridge_entry.py"));
        properties.getPython().setDevice("cpu");
        PythonBridgeAdapter adapter = new PythonBridgeAdapter(properties, objectMapper);

        kr.ac.koreatech.indoor.vps.contexts.mapping.domain.bridge.port.BridgeContracts.HealthBridgeResponse health = adapter.health();

        assertThat(health.mlDevice()).isEqualTo("cpu");
        assertThat(health.cudaVisibleDevices()).isEmpty();
    }

    private BridgeResult call(String command, Map<String, Object> payload) throws Exception {
        Process process = new ProcessBuilder(
                "python3",
                Path.of("scripts/python_bridge/bridge_entry.py").toString(),
                command
        ).start();
        process.getOutputStream().write(objectMapper.writeValueAsBytes(payload));
        process.getOutputStream().close();
        int exitCode = process.waitFor();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new BridgeResult(exitCode, stdout, stderr);
    }

    private record BridgeResult(int exitCode, String stdout, String stderr) {
    }
}
