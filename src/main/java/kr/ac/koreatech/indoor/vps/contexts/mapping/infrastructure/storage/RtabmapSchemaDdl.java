package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

final class RtabmapSchemaDdl {

    static final String NODE = """
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
            """;

    static final String DATA = """
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
            """;

    static final String LINK = """
            CREATE TABLE IF NOT EXISTS Link (
                from_id INTEGER NOT NULL,
                to_id INTEGER NOT NULL,
                type INTEGER NOT NULL,
                information_matrix BLOB NOT NULL,
                transform BLOB,
                user_data BLOB
            )
            """;

    static final String WORD = """
            CREATE TABLE IF NOT EXISTS Word (
                id INTEGER NOT NULL,
                descriptor_size INTEGER NOT NULL,
                descriptor BLOB NOT NULL,
                time_enter DATE,
                PRIMARY KEY (id)
            )
            """;

    static final String FEATURE = """
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
            """;

    static final String GLOBAL_DESCRIPTOR = """
            CREATE TABLE IF NOT EXISTS GlobalDescriptor (
                node_id INTEGER NOT NULL,
                type INTEGER NOT NULL,
                info BLOB,
                data BLOB NOT NULL
            )
            """;

    static final String INFO = """
            CREATE TABLE IF NOT EXISTS Info (
                STM_size INTEGER,
                last_sign_added INTEGER,
                process_mem_used INTEGER,
                database_mem_used INTEGER,
                dictionary_size INTEGER,
                parameters TEXT,
                time_enter DATE
            )
            """;

    static final String STATISTICS = """
            CREATE TABLE IF NOT EXISTS Statistics (
                id INTEGER NOT NULL,
                stamp FLOAT,
                data BLOB,
                wm_state BLOB
            )
            """;

    static final String ADMIN = """
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
            """;

    private RtabmapSchemaDdl() {
    }
}
