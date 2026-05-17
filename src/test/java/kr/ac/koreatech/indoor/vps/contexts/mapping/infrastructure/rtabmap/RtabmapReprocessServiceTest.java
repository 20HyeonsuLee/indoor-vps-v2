package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.config.IndoorProperties;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap.RtabmapReprocessService.RtabmapReprocessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RtabmapReprocessServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void runsConfiguredRtabmapReprocessBinaryAndUsesOutputDb() throws Exception {
        Path inputDb = inputDb();
        Path fakeBinary = executableScript("""
                #!/bin/sh
                echo "fake reprocess"
                cp "$5" "$6"
                """);
        RtabmapReprocessService service = new RtabmapReprocessService(properties(fakeBinary, false));

        RtabmapReprocessService.RtabmapReprocessResult result = service.reprocess(UUID.randomUUID(), inputDb);

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.hasUsableOutput()).isTrue();
        assertThat(result.effectiveDbPath()).isEqualTo(tempDir.resolve("rtabmap_reprocessed.db"));
        assertThat(countRows(result.effectiveDbPath(), "Node")).isEqualTo(1);
        assertThat(result.metadata()).containsEntry("status", "succeeded");
    }

    @Test
    void skipsWhenBinaryIsMissingAndReprocessIsOptional() throws Exception {
        Path inputDb = inputDb();
        RtabmapReprocessService service = new RtabmapReprocessService(properties(tempDir.resolve("missing"), false));

        RtabmapReprocessService.RtabmapReprocessResult result = service.reprocess(UUID.randomUUID(), inputDb);

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.reason()).isEqualTo("binary_not_available");
        assertThat(result.effectiveDbPath()).isEqualTo(inputDb);
    }

    @Test
    void failsWhenBinaryIsMissingAndReprocessIsRequired() throws Exception {
        Path inputDb = inputDb();
        RtabmapReprocessService service = new RtabmapReprocessService(properties(tempDir.resolve("missing"), true));

        assertThatThrownBy(() -> service.reprocess(UUID.randomUUID(), inputDb))
                .isInstanceOf(RtabmapReprocessException.class)
                .hasMessageContaining("rtabmap-reprocess binary not available");
    }

    private IndoorProperties properties(Path executable, boolean required) {
        IndoorProperties properties = new IndoorProperties();
        properties.getRtabmap().getReprocess().setExecutable(executable.toString());
        properties.getRtabmap().getReprocess().setRequired(required);
        properties.getRtabmap().getReprocess().setTimeoutSeconds(5);
        return properties;
    }

    private Path inputDb() throws Exception {
        Path inputDb = tempDir.resolve("rtabmap.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + inputDb);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE Node (
                        id INTEGER PRIMARY KEY,
                        map_id INTEGER,
                        weight INTEGER,
                        stamp FLOAT,
                        pose BLOB NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Data (
                        id INTEGER PRIMARY KEY,
                        image BLOB,
                        depth BLOB,
                        calibration BLOB,
                        scan BLOB,
                        scan_info BLOB,
                        user_data BLOB
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Link (
                        from_id INTEGER,
                        to_id INTEGER,
                        type INTEGER,
                        transform BLOB,
                        information_matrix BLOB,
                        user_data BLOB
                    )
                    """);
            statement.execute("INSERT INTO Node(id, map_id, weight, stamp, pose) VALUES (1, 0, 1, 1.0, zeroblob(48))");
            statement.execute("INSERT INTO Data(id) VALUES (1)");
        }
        return inputDb;
    }

    private int countRows(Path dbPath, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private Path executableScript(String script) throws Exception {
        Path path = tempDir.resolve("rtabmap-reprocess");
        Files.writeString(path, script, StandardCharsets.UTF_8);
        assertThat(path.toFile().setExecutable(true)).isTrue();
        return path;
    }
}
