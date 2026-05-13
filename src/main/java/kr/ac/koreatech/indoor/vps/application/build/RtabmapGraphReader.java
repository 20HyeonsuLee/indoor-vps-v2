package kr.ac.koreatech.indoor.vps.application.build;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.EdgeType;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.NodeType;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapEdgeEntity;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.MapNodeEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class RtabmapGraphReader {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public RtabmapGraph read(Path dbPath, UUID scanId, UUID buildJobId) {
        try {
            JdbcClient jdbcClient = JdbcClient.create(dataSource(dbPath));
            List<NodeRow> nodeRows = readNodeRows(jdbcClient);
            Map<Integer, NodeRow> nodeByRtabmapId = new HashMap<>();
            List<MapNodeEntity> nodes = new ArrayList<>();
            for (NodeRow nodeRow : nodeRows) {
                nodeByRtabmapId.put(nodeRow.rtabmapId(), nodeRow);
                nodes.add(MapNodeEntity.create(
                        nodeId(scanId, nodeRow.rtabmapId()),
                        scanId,
                        buildJobId,
                        NodeType.corridor,
                        geometryFactory.createPoint(new Coordinate(nodeRow.x(), nodeRow.y(), nodeRow.z())),
                        nodeRow.label()
                ));
            }

            List<MapEdgeEntity> edges = new ArrayList<>();
            jdbcClient.sql("SELECT from_id, to_id, type FROM Link ORDER BY from_id, to_id, type")
                    .query((rows, rowNumber) -> new LinkRow(
                            rows.getInt("from_id"),
                            rows.getInt("to_id"),
                            rows.getInt("type")
                    ))
                    .list()
                    .forEach(link -> {
                        NodeRow from = nodeByRtabmapId.get(link.from());
                        NodeRow to = nodeByRtabmapId.get(link.to());
                        if (from == null || to == null || from.rtabmapId() == to.rtabmapId()) {
                            return;
                        }
                        Coordinate fromCoordinate = new Coordinate(from.x(), from.y(), from.z());
                        Coordinate toCoordinate = new Coordinate(to.x(), to.y(), to.z());
                        edges.add(MapEdgeEntity.create(
                                edgeId(scanId, from.rtabmapId(), to.rtabmapId(), link.type()),
                                scanId,
                                buildJobId,
                                nodeId(scanId, from.rtabmapId()),
                                nodeId(scanId, to.rtabmapId()),
                                EdgeType.skeleton,
                                geometryFactory.createLineString(new Coordinate[] {fromCoordinate, toCoordinate}),
                                distance(fromCoordinate, toCoordinate)
                        ));
                    });
            return new RtabmapGraph(nodes, edges);
        } catch (RtabmapGraphReadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RtabmapGraphReadException("failed to read rtabmap graph: " + e.getMessage(), e);
        }
    }

    private SQLiteDataSource dataSource(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    private List<NodeRow> readNodeRows(JdbcClient jdbcClient) {
        return jdbcClient.sql("SELECT id, pose, label FROM Node ORDER BY id")
                .query((rows, rowNumber) -> {
                    Pose pose = decodePose(rows.getBytes("pose"));
                    return new NodeRow(
                            rows.getInt("id"),
                            pose.x(),
                            pose.y(),
                            pose.z(),
                            rows.getString("label")
                    );
                })
                .list();
    }

    private Pose decodePose(byte[] blob) {
        if (blob == null || blob.length != 48) {
            throw new RtabmapGraphReadException("RTAB-Map pose blob must be 48 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[12];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return new Pose(values[3], values[7], values[11]);
    }

    private UUID nodeId(UUID scanId, int rtabmapNodeId) {
        return deterministicUuid("node:" + scanId + ":" + rtabmapNodeId);
    }

    private UUID edgeId(UUID scanId, int from, int to, int type) {
        return deterministicUuid("edge:" + scanId + ":" + from + ":" + to + ":" + type);
    }

    private UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private double distance(Coordinate a, Coordinate b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public record RtabmapGraph(List<MapNodeEntity> nodes, List<MapEdgeEntity> edges) {
    }

    private record NodeRow(int rtabmapId, double x, double y, double z, String label) {
    }

    private record LinkRow(int from, int to, int type) {
    }

    private record Pose(double x, double y, double z) {
    }

    public static class RtabmapGraphReadException extends RuntimeException {
        public RtabmapGraphReadException(String message) {
            super(message);
        }

        public RtabmapGraphReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
