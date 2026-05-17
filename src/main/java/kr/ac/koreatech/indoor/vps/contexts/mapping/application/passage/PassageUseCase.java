package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class PassageUseCase {
    private final BuildingQueryService buildingQuery;
    private final JdbcClient jdbcClient;

    public PassageUseCase(BuildingQueryService buildingQuery, JdbcClient jdbcClient) {
        this.buildingQuery = buildingQuery;
        this.jdbcClient = jdbcClient;
    }

    public List<VerticalPassageResult.Summary> listPassages(UUID buildingId) {
        buildingQuery.requireBuilding(buildingId);
        Map<UUID, PassageAccumulator> passages = new LinkedHashMap<>();
        for (PassageRow row : fetchRows(buildingId)) {
            PassageAccumulator passage = passages.computeIfAbsent(
                    row.passageId(),
                    ignored -> new PassageAccumulator(row)
            );
            if (row.stopId() != null) {
                passage.segments.add(new VerticalPassageResult.Segment(
                        row.stopId().toString(),
                        row.levelId(),
                        row.routeNodeId() == null ? null : row.routeNodeId().toString(),
                        row.x(),
                        row.y(),
                        row.floorId() == null ? null : row.floorId().toString(),
                        row.connectorType()
                ));
            }
        }
        return passages.values().stream()
                .map(PassageAccumulator::toResult)
                .toList();
    }

    private List<PassageRow> fetchRows(UUID buildingId) {
        return jdbcClient.sql("""
                        SELECT
                            vc.connector_id AS passage_id,
                            vc.building_id,
                            vc.connector_type,
                            vc.connector_key,
                            vc.name,
                            vc.is_mock,
                            vcs.connector_stop_id AS stop_id,
                            vcs.level_id,
                            COALESCE(vcs.route_node_id, p.route_node_id) AS route_node_id,
                            COALESCE(p.floor_id, bf.floor_id) AS floor_id,
                            ST_X(COALESCE(p.display_point, mn.geom)) AS x,
                            ST_Y(COALESCE(p.display_point, mn.geom)) AS y
                        FROM vertical_connector vc
                        LEFT JOIN vertical_connector_stop vcs ON vcs.connector_id = vc.connector_id
                        LEFT JOIN poi_canonical p ON p.canonical_id = vcs.poi_canonical_id
                        LEFT JOIN map_node mn ON mn.node_id = COALESCE(vcs.route_node_id, p.route_node_id)
                        LEFT JOIN building_floor bf ON bf.building_id = vc.building_id
                            AND (
                                bf.name = vcs.level_id
                                OR bf.level::text = vcs.level_id
                                OR ('level-' || bf.level::text) = vcs.level_id
                            )
                        WHERE vc.building_id = :buildingId
                        ORDER BY vc.connector_type, vc.connector_key, vcs.level_id
                        """)
                .param("buildingId", buildingId)
                .query(this::toRow)
                .list();
    }

    private PassageRow toRow(ResultSet rs, int rowNum) throws SQLException {
        return new PassageRow(
                rs.getObject("passage_id", UUID.class),
                rs.getObject("building_id", UUID.class),
                rs.getString("connector_type"),
                rs.getString("connector_key"),
                rs.getString("name"),
                rs.getBoolean("is_mock"),
                rs.getObject("stop_id", UUID.class),
                rs.getString("level_id"),
                rs.getObject("route_node_id", UUID.class),
                rs.getObject("floor_id", UUID.class),
                nullableDouble(rs, "x"),
                nullableDouble(rs, "y")
        );
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private record PassageRow(
            UUID passageId,
            UUID buildingId,
            String connectorType,
            String connectorKey,
            String name,
            boolean mock,
            UUID stopId,
            String levelId,
            UUID routeNodeId,
            UUID floorId,
            Double x,
            Double y
    ) {
    }

    private static final class PassageAccumulator {
        private final PassageRow row;
        private final List<VerticalPassageResult.Segment> segments = new ArrayList<>();

        private PassageAccumulator(PassageRow row) {
            this.row = row;
        }

        private VerticalPassageResult.Summary toResult() {
            return new VerticalPassageResult.Summary(
                    row.passageId(),
                    row.buildingId(),
                    row.connectorType(),
                    row.connectorKey(),
                    row.name(),
                    row.mock(),
                    List.copyOf(segments)
            );
        }
    }
}
