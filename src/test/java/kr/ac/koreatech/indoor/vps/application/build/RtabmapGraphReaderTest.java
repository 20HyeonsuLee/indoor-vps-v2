package kr.ac.koreatech.indoor.vps.application.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

class RtabmapGraphReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsRtabmapNodeAndLinkTablesIntoGraphEntities() {
        Path db = tempDir.resolve("rtabmap.db");
        UUID scanId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID buildJobId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        createDatabase(db);

        RtabmapGraphReader.RtabmapGraph graph = new RtabmapGraphReader().read(db, scanId, buildJobId);

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.nodes()).extracting(MapNodeEntity::getLabel)
                .containsExactly("start", "end");
        assertThat(graph.nodes()).extracting(node -> node.getGeom().getX())
                .containsExactly(0.0, 3.0);
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst().getScanId()).isEqualTo(scanId);
        assertThat(graph.edges().getFirst().getBuildJobId()).isEqualTo(buildJobId);
        assertThat(graph.edges().getFirst().getLengthM()).isEqualTo(5.0);
    }

    private void createDatabase(Path db) {
        JdbcClient jdbcClient = JdbcClient.create(dataSource(db));
        jdbcClient.sql("CREATE TABLE Node (id INTEGER, pose BLOB, label TEXT)").update();
        jdbcClient.sql("CREATE TABLE Link (from_id INTEGER, to_id INTEGER, type INTEGER)").update();
        insertNode(jdbcClient, 1, "start", 0, 0, 0);
        insertNode(jdbcClient, 2, "end", 3, 4, 0);
        jdbcClient.sql("INSERT INTO Link (from_id, to_id, type) VALUES (?, ?, ?)")
                .params(1, 2, 0)
                .update();
    }

    private SQLiteDataSource dataSource(Path db) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + db);
        return dataSource;
    }

    private void insertNode(JdbcClient jdbcClient, int id, String label, float x, float y, float z) {
        jdbcClient.sql("INSERT INTO Node (id, pose, label) VALUES (?, ?, ?)")
                .params(id, poseBlob(x, y, z), label)
                .update();
    }

    private byte[] poseBlob(float x, float y, float z) {
        ByteBuffer buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = {
                1, 0, 0, x,
                0, 1, 0, y,
                0, 0, 1, z
        };
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }
}
