package kr.ac.koreatech.indoor.vps.karate.support;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

public class RtabmapScanFixtureFactory {
    public ScanFixture twoConnectedNodes() throws Exception {
        UUID scanId = UUID.randomUUID();
        Path db = Files.createTempFile("indoor-acceptance-rtabmap-", ".db");
        try {
            createRtabmapDb(db);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                zipFile(zip, scanId + "/rtabmap.db", Files.readAllBytes(db));
                zipFile(zip, scanId + "/scan_metadata.db", new byte[] {1});
            }
            return new ScanFixture(
                    scanId,
                    deterministicUuid("node:" + scanId + ":1"),
                    deterministicUuid("node:" + scanId + ":2"),
                    bytes.toByteArray()
            );
        } finally {
            Files.deleteIfExists(db);
        }
    }

    private void createRtabmapDb(Path db) {
        JdbcClient jdbcClient = JdbcClient.create(sqliteDataSource(db));
        jdbcClient.sql("CREATE TABLE Node (id INTEGER, pose BLOB, label TEXT)").update();
        jdbcClient.sql("CREATE TABLE Link (from_id INTEGER, to_id INTEGER, type INTEGER)").update();
        insertNode(jdbcClient, 1, "start", 0, 0, 0);
        insertNode(jdbcClient, 2, "end", 3, 4, 0);
        jdbcClient.sql("INSERT INTO Link (from_id, to_id, type) VALUES (?, ?, ?)")
                .params(1, 2, 0)
                .update();
    }

    private SQLiteDataSource sqliteDataSource(Path db) {
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

    private void zipFile(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public record ScanFixture(UUID scanId, UUID startNodeId, UUID endNodeId, byte[] zipBytes) {
    }
}
