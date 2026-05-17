package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

final class ScanDbStats {
    private ScanDbStats() {
    }

    static Optional<Integer> countTableRows(Path sqlitePath, String tableName) {
        if (!Files.exists(sqlitePath)) {
            return Optional.empty();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
            if (!tableExists(connection, tableName)) {
                return Optional.empty();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                return result.next() ? Optional.of(result.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    static Optional<Integer> maxNodeId(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return Optional.empty();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(id) FROM Node")) {
            return result.next() && result.getObject(1) != null ? Optional.of(result.getInt(1)) : Optional.empty();
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    static Optional<Integer> manifestCount(Path manifestPath, String field, ObjectMapper objectMapper) {
        if (!Files.exists(manifestPath)) {
            return Optional.empty();
        }
        try {
            JsonNode json = objectMapper.readTree(manifestPath.toFile());
            return json.has(field) && json.path(field).canConvertToInt()
                    ? Optional.of(json.path(field).asInt())
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
