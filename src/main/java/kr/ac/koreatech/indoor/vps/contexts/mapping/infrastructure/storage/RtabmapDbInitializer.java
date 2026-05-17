package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class RtabmapDbInitializer {
    private static final String RTABMAP_PARAMS = "Mem/IncrementalMemory=true;Rtabmap/DetectionRate=0;";

    private RtabmapDbInitializer() {
    }

    static void initialize(Path dbPath) throws IOException, SQLException {
        Files.createDirectories(dbPath.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            createTables(connection);
            ensureDataColumns(connection);
            insertDefaultRows(connection);
        }
    }

    static void checkpoint(Path dbPath) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private static void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute(RtabmapSchemaDdl.NODE);
            statement.execute(RtabmapSchemaDdl.DATA);
            statement.execute(RtabmapSchemaDdl.LINK);
            statement.execute(RtabmapSchemaDdl.WORD);
            statement.execute(RtabmapSchemaDdl.FEATURE);
            statement.execute(RtabmapSchemaDdl.GLOBAL_DESCRIPTOR);
            statement.execute(RtabmapSchemaDdl.INFO);
            statement.execute(RtabmapSchemaDdl.STATISTICS);
            statement.execute(RtabmapSchemaDdl.ADMIN);
            statement.execute("CREATE INDEX IF NOT EXISTS IDX_Feature_node_id on Feature (node_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS IDX_GlobalDescriptor_node_id on GlobalDescriptor (node_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS IDX_Link_from_id on Link (from_id)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_Link_unique on Link (from_id, to_id, type)");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS IDX_Statistics_id on Statistics (id)");
        }
    }

    private static void ensureDataColumns(Connection connection) throws SQLException {
        ensureDataColumn(connection, "depth_confidence", "BLOB");
        ensureDataColumn(connection, "ground_cells", "BLOB");
        ensureDataColumn(connection, "obstacle_cells", "BLOB");
        ensureDataColumn(connection, "empty_cells", "BLOB");
        ensureDataColumn(connection, "cell_size", "FLOAT");
        ensureDataColumn(connection, "view_point_x", "FLOAT");
        ensureDataColumn(connection, "view_point_y", "FLOAT");
        ensureDataColumn(connection, "view_point_z", "FLOAT");
        ensureDataColumn(connection, "time_enter", "DATE");
    }

    static void ensureDataColumn(Connection connection, String name, String definition) throws SQLException {
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

    private static void insertDefaultRows(Connection connection) throws SQLException {
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
