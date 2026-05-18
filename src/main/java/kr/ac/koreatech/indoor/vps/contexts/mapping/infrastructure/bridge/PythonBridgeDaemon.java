package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.bridge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PythonBridgeDaemon {
    private static final Logger log = LoggerFactory.getLogger(PythonBridgeDaemon.class);

    private final IndoorProperties properties;
    private final ObjectMapper objectMapper;

    private final Object lifecycleLock = new Object();
    private final Object writeLock = new Object();
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    private Process process;
    private BufferedWriter writer;

    public PythonBridgeDaemon(IndoorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode call(String command, Object payload, Duration timeout) {
        ensureRunning();
        String id = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            send(id, command, payload);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw bridgeError(command, "PYTHON_BRIDGE_TIMEOUT", "Python bridge timed out.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            throw bridgeError(command, "PYTHON_BRIDGE_INTERRUPTED", e.getMessage());
        } catch (ExecutionException e) {
            pending.remove(id);
            if (e.getCause() instanceof ClientApiException ce) {
                throw ce;
            }
            throw bridgeError(command, "PYTHON_BRIDGE_FAILED",
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        } catch (IOException e) {
            pending.remove(id);
            restart();
            throw bridgeError(command, "PYTHON_BRIDGE_FAILED", e.getMessage());
        }
    }

    private void ensureRunning() {
        if (process != null && process.isAlive()) {
            return;
        }
        synchronized (lifecycleLock) {
            if (process != null && process.isAlive()) {
                return;
            }
            start();
        }
    }

    private void start() {
        try {
            ProcessBuilder pb = new ProcessBuilder(List.of(
                    properties.getPython().getExecutable(),
                    properties.getPython().getBridgeScript().toString(),
                    "daemon"
            )).redirectErrorStream(false);
            applyEnvironment(pb);
            Process p = pb.start();
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
            startReaderThread(p);
            startStderrPump(p);
            this.process = p;
            log.info("Python bridge daemon started (pid={})", p.pid());
        } catch (IOException e) {
            throw new ClientApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PYTHON_BRIDGE_START_FAILED",
                    e.getMessage()
            );
        }
    }

    private void restart() {
        synchronized (lifecycleLock) {
            if (process != null) {
                process.destroyForcibly();
            }
            process = null;
            writer = null;
            failAllPending("Python bridge daemon restart.");
        }
    }

    private void applyEnvironment(ProcessBuilder pb) {
        Map<String, String> env = pb.environment();
        env.put("STORAGE_ROOT", properties.getStorageRoot().toAbsolutePath().normalize().toString());
        String backendSource = properties.getPython().getBackendSource();
        if (backendSource != null && !backendSource.isBlank()) {
            env.put("INDOOR_LEGACY_BACKEND_SRC", backendSource);
        }
        String device = properties.getPython().getDevice();
        if (device == null || device.isBlank()) {
            return;
        }
        String normalized = device.trim();
        env.put("INDOOR_ML_DEVICE", normalized);
        if ("cpu".equalsIgnoreCase(normalized)) {
            env.put("CUDA_VISIBLE_DEVICES", "");
        }
    }

    private void send(String id, String command, Object payload) throws IOException {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", id);
        msg.put("command", command);
        msg.put("payload", payload);
        String line = objectMapper.writeValueAsString(msg);
        synchronized (writeLock) {
            if (writer == null) {
                throw new IOException("Python bridge daemon writer is not initialized.");
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
        }
    }

    private void startReaderThread(Process p) {
        Thread t = new Thread(() -> readerLoop(p), "python-bridge-daemon-stdout");
        t.setDaemon(true);
        t.start();
    }

    private void readerLoop(Process p) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            log.warn("Python bridge stdout reader IO error: {}", e.getMessage());
        }
        log.warn("Python bridge daemon stdout closed — process may have exited.");
        failAllPending("Python bridge daemon exited unexpectedly.");
    }

    private void handleLine(String line) {
        try {
            JsonNode msg = objectMapper.readTree(line);
            if (msg.has("event")) {
                log.info("Python bridge event: {}", line);
                return;
            }
            String id = msg.path("id").asText(null);
            if (id == null) {
                log.warn("Python bridge response without id: {}", line);
                return;
            }
            CompletableFuture<JsonNode> future = pending.remove(id);
            if (future == null) {
                log.warn("No pending request for id={}", id);
                return;
            }
            completeFuture(future, msg);
        } catch (IOException e) {
            log.warn("Python bridge response parse error: {} — line={}", e.getMessage(), line);
        }
    }

    private void completeFuture(CompletableFuture<JsonNode> future, JsonNode msg) {
        if (msg.path("ok").asBoolean(false)) {
            future.complete(msg.path("data"));
            return;
        }
        JsonNode error = msg.path("error");
        String code = error.path("code").asText("PYTHON_BRIDGE_FAILED");
        String message = error.path("message").asText("Python bridge failed.");
        Map<String, Object> detail = new LinkedHashMap<>();
        if (error.path("detail").isObject()) {
            detail.putAll(objectMapper.convertValue(
                    error.path("detail"), new TypeReference<Map<String, Object>>() {}));
        }
        future.completeExceptionally(
                new ClientApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message, detail));
    }

    private void startStderrPump(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.info("[python-bridge] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "python-bridge-daemon-stderr");
        t.setDaemon(true);
        t.start();
    }

    private void failAllPending(String reason) {
        ClientApiException error = new ClientApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PYTHON_BRIDGE_FAILED",
                reason
        );
        pending.forEach((id, future) -> future.completeExceptionally(error));
        pending.clear();
    }

    private ClientApiException bridgeError(String command, String code, String message) {
        return new ClientApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                code,
                message,
                Map.of("command", command)
        );
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lifecycleLock) {
            if (process == null || !process.isAlive()) {
                return;
            }
            try {
                if (writer != null) {
                    synchronized (writeLock) {
                        try {
                            writer.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
