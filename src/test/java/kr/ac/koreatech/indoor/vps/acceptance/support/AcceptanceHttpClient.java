package kr.ac.koreatech.indoor.vps.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

public class AcceptanceHttpClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final WebServerApplicationContext webServerContext;

    public AcceptanceHttpClient(ObjectMapper objectMapper, WebServerApplicationContext webServerContext) {
        this.objectMapper = objectMapper;
        this.webServerContext = webServerContext;
    }

    public JsonNode postJson(String path, Map<String, Object> body, int expectedStatus) throws Exception {
        return postJsonResponse(path, body, expectedStatus).body();
    }

    public AcceptanceResponse postJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return send(request);
    }

    public AcceptanceResponse postJsonResponse(String path, Map<String, Object> body, int expectedStatus)
            throws Exception {
        return postJson(path, body).assertStatus(expectedStatus);
    }

    public JsonNode getJson(String path, int expectedStatus) throws Exception {
        return get(path, expectedStatus).body();
    }

    public AcceptanceResponse get(String path) throws Exception {
        return get(path, Map.of());
    }

    public AcceptanceResponse get(String path, int expectedStatus) throws Exception {
        return get(path, Map.of(), expectedStatus);
    }

    public AcceptanceResponse get(String path, Map<String, String> headers, int expectedStatus) throws Exception {
        return get(path, headers).assertStatus(expectedStatus);
    }

    public AcceptanceResponse get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(builder::header);
        return send(builder.build());
    }

    public JsonNode postMultipart(
            String path,
            String fieldName,
            String fileName,
            String contentType,
            byte[] bytes,
            int expectedStatus
    ) throws Exception {
        return postMultipartResponse(path, fieldName, fileName, contentType, bytes, expectedStatus).body();
    }

    public AcceptanceResponse postMultipartResponse(
            String path,
            String fieldName,
            String fileName,
            String contentType,
            byte[] bytes,
            int expectedStatus
    ) throws Exception {
        return postMultipartResponse(path, Map.of(), fieldName, fileName, contentType, bytes, expectedStatus);
    }

    public AcceptanceResponse postMultipartResponse(
            String path,
            Map<String, String> queryParams,
            String fieldName,
            String fileName,
            String contentType,
            byte[] bytes,
            int expectedStatus
    ) throws Exception {
        return postMultipart(path, queryParams, fieldName, fileName, contentType, bytes)
                .assertStatus(expectedStatus);
    }

    public AcceptanceResponse postMultipart(
            String path,
            Map<String, String> queryParams,
            String fieldName,
            String fileName,
            String contentType,
            byte[] bytes
    ) throws Exception {
        String boundary = "----indoor-acceptance-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(uri(path, queryParams))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, fieldName, fileName, contentType, bytes)))
                .build();
        return send(request);
    }

    private AcceptanceResponse send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.body().isBlank()) {
            return new AcceptanceResponse(
                    response.statusCode(),
                    objectMapper.createObjectNode(),
                    response.headers(),
                    response.body()
            );
        }
        return new AcceptanceResponse(
                response.statusCode(),
                objectMapper.readTree(response.body()),
                response.headers(),
                response.body()
        );
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + webServerContext.getWebServer().getPort() + path);
    }

    private URI uri(String path, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return uri(path);
        }
        StringBuilder query = new StringBuilder();
        queryParams.forEach((key, value) -> {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return uri(path + "?" + query);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private byte[] multipart(String boundary, String fieldName, String fileName, String contentType, byte[] bytes) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeAscii(body, "--" + boundary + "\r\n");
        writeAscii(body, "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n");
        writeAscii(body, "Content-Type: " + contentType + "\r\n\r\n");
        body.writeBytes(bytes);
        writeAscii(body, "\r\n--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }

    public record AcceptanceResponse(
            int statusCode,
            JsonNode body,
            HttpHeaders headers,
            String rawBody
    ) {
        public AcceptanceResponse assertStatus(int expectedStatus) {
            assertThat(statusCode)
                    .describedAs("HTTP body: " + rawBody)
                    .isEqualTo(expectedStatus);
            return this;
        }

        public String header(String name) {
            return headers.firstValue(name).orElse("");
        }

        public boolean hasBlankBody() {
            return rawBody == null || rawBody.isBlank();
        }
    }
}
