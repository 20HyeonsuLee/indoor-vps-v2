package kr.ac.koreatech.indoor.vps.application.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PythonBridgeContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void healthReturnsSupportedCommands() throws Exception {
        BridgeResult result = call("health", Map.of());

        assertThat(result.exitCode()).isZero();
        JsonNode stdout = objectMapper.readTree(result.stdout());
        assertThat(stdout.path("ok").asBoolean()).isTrue();
        assertThat(stdout.path("commands").valueStream().map(JsonNode::asText).toList())
                .contains("localize", "merge_scan", "build_floor_map");
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
                "contractOnly", true
        ));
        assertThat(contractOnly.exitCode()).isZero();
    }

    @Test
    void mergeAndBuildCommandsValidateSchemas() throws Exception {
        BridgeResult merge = call("merge_scan", Map.of(
                "floorId", "f1",
                "scanId", "s1",
                "sourcePaths", List.of("/tmp/a.db"),
                "outputDir", "/tmp/out",
                "contractOnly", true
        ));
        BridgeResult build = call("build_floor_map", Map.of(
                "floorId", "f1",
                "scanId", "s1",
                "buildJobId", "j1",
                "scanPath", "/tmp/a.db",
                "outputDir", "/tmp/out",
                "contractOnly", true
        ));

        assertThat(merge.exitCode()).isZero();
        assertThat(build.exitCode()).isZero();
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
