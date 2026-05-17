package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RtabmapReprocessService {
    private static final Logger log = LoggerFactory.getLogger(RtabmapReprocessService.class);
    private static final String RTABMAP_PARAMS = "Mem/IncrementalMemory=true;Rtabmap/DetectionRate=0;";

    private final IndoorProperties properties;

    public RtabmapReprocessService(IndoorProperties properties) {
        this.properties = properties;
    }

    public RtabmapReprocessResult reprocess(UUID scanId, Path inputDb) {
        IndoorProperties.Rtabmap.Reprocess config = properties.getRtabmap().getReprocess();
        if (!config.isEnabled()) {
            return RtabmapReprocessResult.skipped("disabled", inputDb, null, null);
        }
        if (!Files.exists(inputDb)) {
            return RtabmapReprocessResult.skipped("input_db_missing", inputDb, null, null);
        }
        try {
            ensureRtabmapCompatibility(inputDb);
        } catch (IOException | SQLException e) {
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "schema_prepare_failed",
                    inputDb,
                    null,
                    null,
                    List.of(),
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        }
        Optional<Path> binary = resolveExecutable(config.getExecutable());
        if (binary.isEmpty()) {
            RtabmapReprocessResult result = RtabmapReprocessResult.skipped(
                    "binary_not_available",
                    inputDb,
                    null,
                    Map.of("executable", config.getExecutable())
            );
            if (config.isRequired()) {
                throw new RtabmapReprocessException("rtabmap-reprocess binary not available: " + config.getExecutable(), result);
            }
            return result;
        }

        Path outputDb = inputDb.getParent().resolve("rtabmap_reprocessed.db");
        if (Files.exists(outputDb) && hasGraphRows(outputDb)) {
            return RtabmapReprocessResult.alreadyReprocessed(inputDb, outputDb, binary.get());
        }
        if (Files.exists(outputDb)) {
            try {
                Files.delete(outputDb);
            } catch (IOException e) {
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "stale_output_delete_failed",
                        inputDb,
                        outputDb,
                        binary.get(),
                        List.of(),
                        Duration.ZERO,
                        null,
                        "",
                        e.getMessage()
                );
                return failOrFallback(config, result);
            }
        }

        Path stdoutLog = inputDb.getParent().resolve("rtabmap_reprocess.stdout.log");
        Path stderrLog = inputDb.getParent().resolve("rtabmap_reprocess.stderr.log");
        List<String> command = List.of(
                binary.get().toString(),
                "--Kp/DetectorStrategy=1",
                "--Vis/FeatureType=1",
                "--Mem/ImagePreDecimation=1",
                "--Mem/DepthAsMask=true",
                inputDb.toString(),
                outputDb.toString()
        );

        try {
            Files.deleteIfExists(outputDb);
            Files.createDirectories(inputDb.getParent());
            Instant startedAt = Instant.now();
            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdoutLog.toFile())
                    .redirectError(stderrLog.toFile())
                    .start();
            boolean finished = process.waitFor(Math.max(1, config.getTimeoutSeconds()), TimeUnit.SECONDS);
            Duration duration = Duration.between(startedAt, Instant.now());
            if (!finished) {
                process.destroyForcibly();
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "timeout",
                        inputDb,
                        outputDb,
                        binary.get(),
                        command,
                        duration,
                        -1,
                        tail(stdoutLog),
                        tail(stderrLog)
                );
                return failOrFallback(config, result);
            }
            if (process.exitValue() != 0 || !Files.exists(outputDb)) {
                RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                        "exit_" + process.exitValue(),
                        inputDb,
                        outputDb,
                        binary.get(),
                        command,
                        duration,
                        process.exitValue(),
                        tail(stdoutLog),
                        tail(stderrLog)
                );
                return failOrFallback(config, result);
            }
            return RtabmapReprocessResult.succeeded(
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    duration,
                    tail(stdoutLog),
                    tail(stderrLog)
            );
        } catch (IOException e) {
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "io_error",
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RtabmapReprocessResult result = RtabmapReprocessResult.failed(
                    "interrupted",
                    inputDb,
                    outputDb,
                    binary.get(),
                    command,
                    Duration.ZERO,
                    null,
                    "",
                    e.getMessage()
            );
            return failOrFallback(config, result);
        }
    }

    private RtabmapReprocessResult failOrFallback(
            IndoorProperties.Rtabmap.Reprocess config,
            RtabmapReprocessResult result
    ) {
        if (config.isRequired()) {
            throw new RtabmapReprocessException("rtabmap-reprocess failed: " + result.reason(), result);
        }
        log.warn("rtabmap-reprocess failed; using raw rtabmap.db. reason={}", result.reason());
        return result;
    }

    private void ensureRtabmapCompatibility(Path dbPath) throws IOException, SQLException {
        Files.createDirectories(dbPath.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Word (
                            id INTEGER NOT NULL,
                            descriptor_size INTEGER NOT NULL,
                            descriptor BLOB NOT NULL,
                            time_enter DATE,
                            PRIMARY KEY (id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Feature (
                            node_id INTEGER NOT NULL,
                            word_id INTEGER NOT NULL,
                            pos_x FLOAT NOT NULL,
                            pos_y FLOAT NOT NULL,
                            size INTEGER NOT NULL,
                            dir FLOAT NOT NULL,
                            response FLOAT NOT NULL,
                            octave INTEGER NOT NULL,
                            depth_x FLOAT,
                            depth_y FLOAT,
                            depth_z FLOAT,
                            descriptor_size INTEGER,
                            descriptor BLOB
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS GlobalDescriptor (
                            node_id INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            info BLOB,
                            data BLOB NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Info (
                            STM_size INTEGER,
                            last_sign_added INTEGER,
                            process_mem_used INTEGER,
                            database_mem_used INTEGER,
                            dictionary_size INTEGER,
                            parameters TEXT,
                            time_enter DATE
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Statistics (
                            id INTEGER NOT NULL,
                            stamp FLOAT,
                            data BLOB,
                            wm_state BLOB
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS Admin (
                            version TEXT,
                            preview_image BLOB,
                            opt_cloud BLOB,
                            opt_ids BLOB,
                            opt_poses BLOB,
                            opt_last_localization BLOB,
                            opt_polygons_size INTEGER,
                            opt_polygons BLOB,
                            opt_tex_coords BLOB,
                            opt_tex_materials BLOB,
                            opt_map BLOB,
                            opt_map_x_min FLOAT,
                            opt_map_y_min FLOAT,
                            opt_map_resolution FLOAT,
                            dictionary_index BLOB,
                            time_enter DATE
                        )
                        """);
            }
            ensureDataColumn(connection, "depth_confidence", "BLOB");
            ensureDataColumn(connection, "ground_cells", "BLOB");
            ensureDataColumn(connection, "obstacle_cells", "BLOB");
            ensureDataColumn(connection, "empty_cells", "BLOB");
            ensureDataColumn(connection, "cell_size", "FLOAT");
            ensureDataColumn(connection, "view_point_x", "FLOAT");
            ensureDataColumn(connection, "view_point_y", "FLOAT");
            ensureDataColumn(connection, "view_point_z", "FLOAT");
            ensureDataColumn(connection, "time_enter", "DATE");
            try (PreparedStatement adminInsert = connection.prepareStatement("""
                    INSERT INTO Admin(version, time_enter)
                    SELECT '0.23.5', datetime('now')
                    WHERE NOT EXISTS (SELECT 1 FROM Admin)
                    """);
                 PreparedStatement infoInsert = connection.prepareStatement("""
                         INSERT INTO Info(
                            STM_size, last_sign_added, process_mem_used, database_mem_used, dictionary_size, parameters, time_enter
                         )
                         SELECT 0, 0, 0, 0, 0, ?, datetime('now')
                         WHERE NOT EXISTS (SELECT 1 FROM Info)
                         """)) {
                adminInsert.executeUpdate();
                infoInsert.setString(1, RTABMAP_PARAMS);
                infoInsert.executeUpdate();
            }
        }
    }

    private void ensureDataColumn(Connection connection, String name, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(Data)");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Data ADD COLUMN " + name + " " + definition);
        }
    }

    private boolean hasGraphRows(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM Node")) {
            return result.next() && result.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private Optional<Path> resolveExecutable(String executable) {
        if (executable == null || executable.isBlank()) {
            return Optional.empty();
        }
        Path configured = Path.of(executable);
        if (configured.isAbsolute() || configured.getParent() != null) {
            return Files.isExecutable(configured) ? Optional.of(configured) : Optional.empty();
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(entry).resolve(executable);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String tail(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(path);
        int start = Math.max(0, bytes.length - 2000);
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    public static final class RtabmapReprocessException extends RuntimeException {
        private final RtabmapReprocessResult result;

        public RtabmapReprocessException(String message, RtabmapReprocessResult result) {
            super(message);
            this.result = result;
        }

        public RtabmapReprocessResult result() {
            return result;
        }
    }

    public record RtabmapReprocessResult(
            String status,
            String reason,
            Path inputDbPath,
            Path outputDbPath,
            Path binaryPath,
            List<String> command,
            Duration duration,
            Integer exitCode,
            String stdoutTail,
            String stderrTail
    ) {
        static RtabmapReprocessResult skipped(String reason, Path inputDb, Path outputDb, Map<String, Object> detail) {
            return new RtabmapReprocessResult(
                    "skipped",
                    reason,
                    inputDb,
                    outputDb,
                    null,
                    List.of(),
                    Duration.ZERO,
                    null,
                    "",
                    detail == null ? "" : detail.toString()
            );
        }

        static RtabmapReprocessResult alreadyReprocessed(Path inputDb, Path outputDb, Path binary) {
            return new RtabmapReprocessResult(
                    "succeeded",
                    "already_reprocessed",
                    inputDb,
                    outputDb,
                    binary,
                    List.of(),
                    Duration.ZERO,
                    0,
                    "",
                    ""
            );
        }

        static RtabmapReprocessResult succeeded(
                Path inputDb,
                Path outputDb,
                Path binary,
                List<String> command,
                Duration duration,
                String stdoutTail,
                String stderrTail
        ) {
            return new RtabmapReprocessResult(
                    "succeeded",
                    "completed",
                    inputDb,
                    outputDb,
                    binary,
                    List.copyOf(command),
                    duration,
                    0,
                    stdoutTail,
                    stderrTail
            );
        }

        static RtabmapReprocessResult failed(
                String reason,
                Path inputDb,
                Path outputDb,
                Path binary,
                List<String> command,
                Duration duration,
                Integer exitCode,
                String stdoutTail,
                String stderrTail
        ) {
            return new RtabmapReprocessResult(
                    "failed",
                    reason,
                    inputDb,
                    outputDb,
                    binary,
                    List.copyOf(command),
                    duration,
                    exitCode,
                    stdoutTail,
                    stderrTail
            );
        }

        public boolean hasUsableOutput() {
            return "succeeded".equals(status) && outputDbPath != null && Files.exists(outputDbPath);
        }

        public Map<String, Object> metadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("status", status);
            metadata.put("reason", reason);
            metadata.put("input_db_path", inputDbPath == null ? null : inputDbPath.toString());
            metadata.put("output_db_path", outputDbPath == null ? null : outputDbPath.toString());
            metadata.put("binary_path", binaryPath == null ? null : binaryPath.toString());
            metadata.put("duration_ms", duration == null ? 0 : duration.toMillis());
            metadata.put("exit_code", exitCode);
            metadata.put("command", new ArrayList<>(command));
            metadata.put("stdout_tail", stdoutTail == null ? "" : stdoutTail);
            metadata.put("stderr_tail", stderrTail == null ? "" : stderrTail);
            return metadata;
        }

        public Path effectiveDbPath() {
            return hasUsableOutput() ? outputDbPath : inputDbPath;
        }
    }
}
