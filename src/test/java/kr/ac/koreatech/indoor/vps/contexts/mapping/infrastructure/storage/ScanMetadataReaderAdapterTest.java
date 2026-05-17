package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader.ScanMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

class ScanMetadataReaderAdapterTest {

    @TempDir
    Path tempDir;

    private final ScanMetadataReaderAdapter adapter = new ScanMetadataReaderAdapter();

    @Test
    void returnsEmptyWhenFileNotFound() {
        Optional<ScanMetadata> result = adapter.read(tempDir.resolve("nonexistent.db"));

        assertThat(result).isEmpty();
    }

    @Test
    void readsBranchMarksAndPoiMarks() {
        Path db = tempDir.resolve("scan_metadata.db");
        createFixtureDb(db);

        Optional<ScanMetadata> result = adapter.read(db);

        assertThat(result).isPresent();
        ScanMetadata metadata = result.get();
        assertThat(metadata.session().deviceModel()).isEqualTo("iPhone 14 Pro");
        assertThat(metadata.session().buildingId()).isEqualTo("bldg-001");
        assertThat(metadata.session().floorLevel()).isEqualTo("level-1");
        assertThat(metadata.branchMarks()).hasSize(2);
        assertThat(metadata.branchMarks().get(0).nodeType()).isEqualTo("corridor");
        assertThat(metadata.branchMarks().get(1).nodeType()).isEqualTo("corner");
        assertThat(metadata.branchEdges()).isEmpty();
        assertThat(metadata.poiMarks()).hasSize(1);
        assertThat(metadata.poiMarks().getFirst().label()).isEqualTo("강의실 101");
        assertThat(metadata.interfloorMarks()).hasSize(1);
        assertThat(metadata.interfloorMarks().getFirst().connectorType()).isEqualTo("elevator");
    }

    @Test
    void returnsEmptyWhenNoSession() {
        Path db = tempDir.resolve("empty_metadata.db");
        createEmptyDb(db);

        Optional<ScanMetadata> result = adapter.read(db);

        assertThat(result).isEmpty();
    }

    private void createFixtureDb(Path db) {
        JdbcClient jdbc = JdbcClient.create(dataSource(db));

        jdbc.sql("""
                CREATE TABLE scan_session (
                    id INTEGER PRIMARY KEY,
                    started_at TEXT,
                    ended_at TEXT,
                    device_model TEXT,
                    keyframe_count INTEGER,
                    notes TEXT
                )""").update();

        jdbc.sql("""
                INSERT INTO scan_session (started_at, device_model, notes)
                VALUES (?, ?, ?)
                """)
                .params(
                        "2024-01-01T10:00:00Z",
                        "iPhone 14 Pro",
                        """
                        {"buildingId":"bldg-001","floorId":"floor-001","floorLevel":"level-1"}
                        """
                )
                .update();

        jdbc.sql("""
                CREATE TABLE keyframe_meta (
                    scan_id INTEGER, seq INTEGER, captured_at TEXT,
                    image_path TEXT, pose_matrix BLOB,
                    tx REAL, ty REAL, tz REAL,
                    tracking_state TEXT, rtabmap_node_id INTEGER
                )""").update();

        jdbc.sql("""
                CREATE TABLE branch_mark (
                    id INTEGER PRIMARY KEY, scan_id INTEGER,
                    keyframe_seq INTEGER, created_at TEXT,
                    pose_matrix BLOB, tx REAL, ty REAL, tz REAL,
                    node_type TEXT, width_m REAL,
                    connect_hint TEXT, connect_node_id INTEGER,
                    mark_session_id INTEGER, dx_local REAL, dy_local REAL, dz_local REAL
                )""").update();

        jdbc.sql("INSERT INTO branch_mark (id, keyframe_seq, node_type, tx, ty, tz, scan_id) VALUES (1, 5, 'corridor', 0.1, 0.2, 0.3, 1)").update();
        jdbc.sql("INSERT INTO branch_mark (id, keyframe_seq, node_type, tx, ty, tz, connect_hint, scan_id) VALUES (2, 10, 'corner', 1.0, 0.5, 0.0, 'turn_right', 1)").update();

        jdbc.sql("""
                CREATE TABLE poi_mark (
                    id INTEGER PRIMARY KEY, scan_id INTEGER,
                    keyframe_seq INTEGER, created_at TEXT,
                    pose_matrix BLOB, tx REAL, ty REAL, tz REAL,
                    label TEXT, source TEXT,
                    dx_local REAL, dy_local REAL, dz_local REAL
                )""").update();

        jdbc.sql("INSERT INTO poi_mark (id, keyframe_seq, tx, ty, tz, label, scan_id) VALUES (1, 15, 2.0, 0.0, 1.0, '강의실 101', 1)").update();

        jdbc.sql("""
                CREATE TABLE interfloor_mark (
                    id INTEGER PRIMARY KEY, scan_id INTEGER,
                    keyframe_seq INTEGER, created_at TEXT,
                    connector_type TEXT, prefix TEXT,
                    pose_matrix BLOB, tx REAL, ty REAL, tz REAL,
                    dx_local REAL, dy_local REAL, dz_local REAL
                )""").update();

        jdbc.sql("INSERT INTO interfloor_mark (id, keyframe_seq, connector_type, prefix, tx, ty, tz, scan_id) VALUES (1, 20, 'elevator', 'EV-A', 3.0, 0.0, 0.5, 1)").update();
    }

    private void createEmptyDb(Path db) {
        JdbcClient jdbc = JdbcClient.create(dataSource(db));
        jdbc.sql("""
                CREATE TABLE scan_session (
                    id INTEGER PRIMARY KEY, device_model TEXT,
                    started_at TEXT, notes TEXT
                )""").update();
        jdbc.sql("CREATE TABLE keyframe_meta (scan_id INTEGER, seq INTEGER, rtabmap_node_id INTEGER, tx REAL, ty REAL, tz REAL)").update();
        jdbc.sql("CREATE TABLE branch_mark (id INTEGER PRIMARY KEY, scan_id INTEGER, keyframe_seq INTEGER, node_type TEXT, tx REAL, ty REAL, tz REAL, connect_hint TEXT, connect_node_id INTEGER, mark_session_id INTEGER)").update();
        jdbc.sql("CREATE TABLE poi_mark (id INTEGER PRIMARY KEY, scan_id INTEGER, keyframe_seq INTEGER, tx REAL, ty REAL, tz REAL, label TEXT)").update();
        jdbc.sql("CREATE TABLE interfloor_mark (id INTEGER PRIMARY KEY, scan_id INTEGER, keyframe_seq INTEGER, connector_type TEXT, prefix TEXT, tx REAL, ty REAL, tz REAL)").update();
    }

    private SQLiteDataSource dataSource(Path db) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + db);
        return ds;
    }
}
