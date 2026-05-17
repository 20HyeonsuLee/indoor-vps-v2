package kr.ac.koreatech.indoor.vps.karate.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StreamingScanFixtureFactory {
    public StreamingFixture twoLinkedFrames() throws IOException, SQLException {
        UUID scanId = UUID.randomUUID();
        return new StreamingFixture(
                scanId,
                Map.of("scanId", scanId.toString(), "deviceInfo", "{\"model\":\"acceptance\"}"),
                Map.of(
                        "frames", List.of(
                                frame(1, 0.1, pose(0.0f, 0.0f, 0.0f), "start"),
                                frame(2, 0.2, pose(5.0f, 0.0f, 0.0f), "end")
                        ),
                        "links", List.of(Map.of(
                                "fromId", 1,
                                "toId", 2,
                                "transform", pose(5.0f, 0.0f, 0.0f),
                                "type", 0
                        ))
                ),
                manifest(scanId),
                metadata(scanId)
        );
    }

    private Map<String, Object> frame(int nodeId, double stamp, String pose, String label) {
        return Map.of(
                "nodeId", nodeId,
                "stamp", stamp,
                "pose", pose,
                "image", "",
                "calibration", "",
                "label", label
        );
    }

    private byte[] manifest(UUID scanId) {
        return """
                {
                  "metadata_version": 6,
                  "scan_id": "%s",
                  "keyframes_included": false,
                  "keyframe_image_source": "rtabmap_db",
                  "sidecar_keyframe_meta_count": 2
                }
                """.formatted(scanId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] metadata(UUID scanId) throws IOException, SQLException {
        Path path = Files.createTempFile("streaming-scan-metadata-", ".db");
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE keyframe_meta (scan_id TEXT, seq INTEGER)");
                statement.execute("CREATE TABLE poi_mark (id TEXT)");
                statement.execute("INSERT INTO keyframe_meta(scan_id, seq) VALUES ('" + scanId + "', 1)");
                statement.execute("INSERT INTO keyframe_meta(scan_id, seq) VALUES ('" + scanId + "', 2)");
            }
            return Files.readAllBytes(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String pose(float x, float y, float z) {
        ByteBuffer buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[] {
                1.0f, 0.0f, 0.0f, x,
                0.0f, 1.0f, 0.0f, y,
                0.0f, 0.0f, 1.0f, z
        };
        for (float value : values) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public record StreamingFixture(
            UUID scanId,
            Map<String, Object> startBody,
            Map<String, Object> framesBody,
            byte[] manifestBytes,
            byte[] metadataBytes
    ) {
    }
}
