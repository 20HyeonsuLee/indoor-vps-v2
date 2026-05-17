package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.scan.port.ScanMetadataReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class ScanMetadataReaderAdapter implements ScanMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(ScanMetadataReaderAdapter.class);

    @Override
    public Optional<ScanMetadata> read(Path metadataDbPath) {
        if (!Files.exists(metadataDbPath)) {
            log.debug("scan_metadata.db not found at {}, skipping metadata integration", metadataDbPath);
            return Optional.empty();
        }
        try {
            JdbcClient jdbc = JdbcClient.create(dataSource(metadataDbPath));
            SessionInfo session = readSession(jdbc);
            if (session == null) {
                return Optional.empty();
            }
            return Optional.of(new ScanMetadata(
                    session,
                    readKeyframes(jdbc),
                    readBranchMarks(jdbc),
                    readBranchEdges(jdbc),
                    readPoiMarks(jdbc),
                    readInterfloorMarks(jdbc)
            ));
        } catch (RuntimeException e) {
            throw new ScanMetadataReadException("failed to read scan_metadata.db: " + e.getMessage(), e);
        }
    }

    private SessionInfo readSession(JdbcClient jdbc) {
        return jdbc.sql("""
                SELECT device_model, started_at, notes
                FROM scan_session
                ORDER BY rowid LIMIT 1
                """)
                .query((rs, rowNum) -> {
                    String notes = rs.getString("notes");
                    NotesJson parsed = NotesJson.parse(notes);
                    return new SessionInfo(
                            rs.getString("device_model"),
                            rs.getString("started_at"),
                            parsed.buildingId(),
                            parsed.floorId(),
                            parsed.floorLevel()
                    );
                })
                .optional()
                .orElse(null);
    }

    private List<KeyframeRow> readKeyframes(JdbcClient jdbc) {
        return jdbc.sql("""
                SELECT seq, rtabmap_node_id, tx, ty, tz
                FROM keyframe_meta
                ORDER BY seq
                """)
                .query((rs, rowNum) -> {
                    int nodeId = rs.getInt("rtabmap_node_id");
                    Integer rtabmapNodeId = rs.wasNull() ? null : nodeId;
                    return new KeyframeRow(
                            rs.getInt("seq"),
                            rtabmapNodeId,
                            rs.getDouble("tx"),
                            rs.getDouble("ty"),
                            rs.getDouble("tz")
                    );
                })
                .list();
    }

    private List<BranchMarkRow> readBranchMarks(JdbcClient jdbc) {
        return jdbc.sql("""
                SELECT id, keyframe_seq, node_type, tx, ty, tz,
                       connect_hint, connect_node_id, mark_session_id
                FROM branch_mark
                ORDER BY id
                """)
                .query((rs, rowNum) -> {
                    long connectNodeIdVal = rs.getLong("connect_node_id");
                    Long connectNodeId = rs.wasNull() ? null : connectNodeIdVal;
                    long markSessionIdVal = rs.getLong("mark_session_id");
                    Long markSessionId = rs.wasNull() ? null : markSessionIdVal;
                    return new BranchMarkRow(
                            rs.getLong("id"),
                            rs.getInt("keyframe_seq"),
                            rs.getString("node_type"),
                            rs.getDouble("tx"),
                            rs.getDouble("ty"),
                            rs.getDouble("tz"),
                            rs.getString("connect_hint"),
                            connectNodeId,
                            markSessionId
                    );
                })
                .list();
    }

    private List<BranchEdgeRow> readBranchEdges(JdbcClient jdbc) {
        try {
            return jdbc.sql("""
                    SELECT id, from_node_id, to_node_id, kind
                    FROM branch_edge
                    ORDER BY id
                    """)
                    .query((rs, rowNum) -> new BranchEdgeRow(
                            rs.getLong("id"),
                            rs.getLong("from_node_id"),
                            rs.getLong("to_node_id"),
                            rs.getString("kind")
                    ))
                    .list();
        } catch (RuntimeException e) {
            log.debug("branch_edge table not found or unreadable, skipping: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PoiMarkRow> readPoiMarks(JdbcClient jdbc) {
        return jdbc.sql("""
                SELECT id, keyframe_seq, tx, ty, tz, label
                FROM poi_mark
                ORDER BY id
                """)
                .query((rs, rowNum) -> new PoiMarkRow(
                        rs.getLong("id"),
                        rs.getInt("keyframe_seq"),
                        rs.getDouble("tx"),
                        rs.getDouble("ty"),
                        rs.getDouble("tz"),
                        rs.getString("label")
                ))
                .list();
    }

    private List<InterfloorMarkRow> readInterfloorMarks(JdbcClient jdbc) {
        return jdbc.sql("""
                SELECT id, keyframe_seq, connector_type, prefix, tx, ty, tz
                FROM interfloor_mark
                ORDER BY id
                """)
                .query((rs, rowNum) -> new InterfloorMarkRow(
                        rs.getLong("id"),
                        rs.getInt("keyframe_seq"),
                        rs.getString("connector_type"),
                        rs.getString("prefix"),
                        rs.getDouble("tx"),
                        rs.getDouble("ty"),
                        rs.getDouble("tz")
                ))
                .list();
    }

    private SQLiteDataSource dataSource(Path dbPath) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath);
        return ds;
    }

    /** scan_session.notes JSON 파싱 — 순수 문자열 파싱으로 외부 의존 없음 */
    private record NotesJson(String buildingId, String floorId, String floorLevel) {

        static NotesJson parse(String notes) {
            if (notes == null || notes.isBlank()) {
                return new NotesJson(null, null, null);
            }
            return new NotesJson(
                    extractJsonString(notes, "buildingId"),
                    extractJsonString(notes, "floorId"),
                    extractJsonString(notes, "floorLevel")
            );
        }

        private static String extractJsonString(String json, String key) {
            String search = "\"" + key + "\"";
            int keyIdx = json.indexOf(search);
            if (keyIdx < 0) {
                return null;
            }
            int colonIdx = json.indexOf(':', keyIdx + search.length());
            if (colonIdx < 0) {
                return null;
            }
            int quoteStart = json.indexOf('"', colonIdx + 1);
            if (quoteStart < 0) {
                return null;
            }
            int quoteEnd = json.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) {
                return null;
            }
            String value = json.substring(quoteStart + 1, quoteEnd);
            return value.isBlank() ? null : value;
        }
    }
}
