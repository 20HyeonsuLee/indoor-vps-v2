package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.app.IndoorVpsV2Application;
import kr.ac.koreatech.indoor.vps.app.TestApplicationBeans;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.PassageApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = IndoorVpsV2Application.class)
@AutoConfigureMockMvc
@Import(TestApplicationBeans.class)
class OpenApiContractSmokeTest {
    private static final Set<String> EXPECTED_PATHS = Set.of(
            "/api/slam/v3/localize",
            "/api/v1/buildings",
            "/api/v1/buildings/{buildingId}",
            "/api/v1/buildings/{buildingId}/floors",
            "/api/v1/buildings/{buildingId}/pathfinding",
            "/api/v1/buildings/{buildingId}/passages",
            "/api/v1/buildings/{buildingId}/pois",
            "/api/v1/buildings/{buildingId}/pois/search",
            "/api/v1/buildings/{buildingId}/status",
            "/api/v1/floors/{floorId}",
            "/api/v1/floors/{floorId}/map",
            "/api/v1/floors/{floorId}/path",
            "/api/v1/floors/{floorId}/build",
            "/api/v1/floors/{floorId}/process",
            "/api/v1/floors/{floorId}/process/status",
            "/api/v1/floors/{floorId}/route",
            "/api/v1/floors/{floorId}/scans/start",
            "/api/v1/floors/{floorId}/scans/chunks",
            "/api/v1/floors/{floorId}/scans/chunks/{chunkId}",
            "/api/v1/floors/{floorId}/scans/merge",
            "/api/v1/floors/{floorId}/scans/merge/status",
            "/api/v1/scans/{scanId}/frames",
            "/api/v1/scans/{scanId}/finalize"
    );

    private static final Map<String, Set<String>> EXPECTED_METHODS = Map.ofEntries(
            Map.entry("/api/slam/v3/localize", Set.of("post")),
            Map.entry("/api/v1/buildings", Set.of("get", "post")),
            Map.entry("/api/v1/buildings/{buildingId}", Set.of("delete", "get", "put")),
            Map.entry("/api/v1/buildings/{buildingId}/floors", Set.of("get", "post")),
            Map.entry("/api/v1/buildings/{buildingId}/pathfinding", Set.of("post")),
            Map.entry("/api/v1/buildings/{buildingId}/passages", Set.of("get")),
            Map.entry("/api/v1/buildings/{buildingId}/pois", Set.of("get")),
            Map.entry("/api/v1/buildings/{buildingId}/pois/search", Set.of("get")),
            Map.entry("/api/v1/buildings/{buildingId}/status", Set.of("patch")),
            Map.entry("/api/v1/floors/{floorId}", Set.of("delete", "get", "put")),
            Map.entry("/api/v1/floors/{floorId}/map", Set.of("get")),
            Map.entry("/api/v1/floors/{floorId}/path", Set.of("get")),
            Map.entry("/api/v1/floors/{floorId}/build", Set.of("post")),
            Map.entry("/api/v1/floors/{floorId}/process", Set.of("post")),
            Map.entry("/api/v1/floors/{floorId}/process/status", Set.of("get")),
            Map.entry("/api/v1/floors/{floorId}/route", Set.of("get")),
            Map.entry("/api/v1/floors/{floorId}/scans/start", Set.of("post")),
            Map.entry("/api/v1/floors/{floorId}/scans/chunks", Set.of("get", "post")),
            Map.entry("/api/v1/floors/{floorId}/scans/chunks/{chunkId}", Set.of("delete")),
            Map.entry("/api/v1/floors/{floorId}/scans/merge", Set.of("post")),
            Map.entry("/api/v1/floors/{floorId}/scans/merge/status", Set.of("get")),
            Map.entry("/api/v1/scans/{scanId}/frames", Set.of("post")),
            Map.entry("/api/v1/scans/{scanId}/finalize", Set.of("post"))
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PassageApplicationService passageApplicationService;

    @Test
    void openApiContainsCleanedPathSurfaceOnly() throws Exception {
        String body = mockMvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paths = new ObjectMapper().readTree(body).path("paths");

        assertThat(paths.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
        EXPECTED_METHODS.forEach((path, methods) ->
                assertThat(paths.path(path).fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(methods));
    }

    @Test
    void compatibilityDocsAliasesRedirect() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/openapi.json"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/docs"));
    }

    @Test
    void invalidUuidUsesClientErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/buildings/not-a-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("request validation failed"))
                .andExpect(jsonPath("$.detail.errors").isArray());
    }

    @Test
    void buildingPassagesEndpointReturnsArray() throws Exception {
        when(passageApplicationService.listPassages(any(UUID.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/buildings/{buildingId}/passages", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void disabledPythonBridgeUsesClientErrorEnvelope() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "frame.jpg",
                "image/jpeg",
                "fake-image".getBytes()
        );

        mockMvc.perform(multipart("/api/slam/v3/localize")
                        .file(image)
                        .param("building_id", "building-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PYTHON_BRIDGE_DISABLED"));
    }
}
