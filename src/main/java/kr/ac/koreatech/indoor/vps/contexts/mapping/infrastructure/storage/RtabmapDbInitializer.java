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
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Node (
                        id INTEGER NOT NULL,
                        map_id INTEGER NOT NULL,
                        weight INTEGER,
                        stamp FLOAT,
                        pose BLOB NOT NULL,
                        ground_truth_pose BLOB,
                        velocity BLOB,
                        label TEXT,
                        gps BLOB,
                        env_sensors BLOB,
                        time_enter DATE,
                        PRIMARY KEY (id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Data (
                        id INTEGER NOT NULL,
                        image BLOB,
                        depth BLOB,
                        depth_confidence BLOB,
                        calibration BLOB,
                        scan BLOB,
                        scan_info BLOB,
                        ground_cells BLOB,
                        obstacle_cells BLOB,
                        empty_cells BLOB,
                        cell_size FLOAT,
                        view_point_x FLOAT,
                        view_point_y FLOAT,
                        view_point_z FLOAT,
                        user_data BLOB,
                        time_enter DATE,
                        PRIMARY KEY (id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS Link (
                        from_id INTEGER NOT NULL,
                        to_id INTEGER NOT NULL,
                        type INTEGER NOT NULL,
                        information_matrix BLOB NOT NULL,
                        transform BLOB,
                        user_data BLOB
                    )
                    """);
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
