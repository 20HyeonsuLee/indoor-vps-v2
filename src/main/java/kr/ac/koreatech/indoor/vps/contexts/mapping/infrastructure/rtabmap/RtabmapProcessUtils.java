package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.rtabmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

final class RtabmapProcessUtils {

    private RtabmapProcessUtils() {
    }

    static boolean hasGraphRows(Path dbPath) {
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

    static Optional<Path> resolveExecutable(String executable) {
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

    static String tail(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(path);
        int start = Math.max(0, bytes.length - 2000);
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }
}
