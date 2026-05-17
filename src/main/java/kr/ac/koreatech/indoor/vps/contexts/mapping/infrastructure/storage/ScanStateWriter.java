package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ScanStateWriter {
    private final ObjectMapper objectMapper;

    ScanStateWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void writeState(
            Path statePath,
            UUID floorId,
            UUID scanId,
            String storagePath,
            Map<String, Object> deviceInfo,
            String state,
            int lastNodeId,
            int nodeCount,
            String finalizedAt
    ) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("floorId", floorId);
        body.put("scanId", scanId);
        body.put("storagePath", storagePath);
        body.put("state", state);
        body.put("deviceInfo", deviceInfo);
        body.put("lastNodeId", lastNodeId);
        body.put("nodeCount", nodeCount);
        body.put("updatedAt", Instant.now().toString());
        if (finalizedAt != null) {
            body.put("finalizedAt", finalizedAt);
        }
        writeJson(statePath, body);
    }

    void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }
}
