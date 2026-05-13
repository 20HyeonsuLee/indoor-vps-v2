package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.EdgeType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "map_edge")
public class MapEdgeEntity {
    @Id
    @Column(name = "edge_id", nullable = false)
    private UUID edgeId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "build_job_id", nullable = false)
    private UUID buildJobId;

    @Column(name = "from_node_id", nullable = false)
    private UUID fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private UUID toNodeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "edge_type", nullable = false, columnDefinition = "edge_type")
    private EdgeType edgeType;

    @Column(columnDefinition = "geometry(LineStringZ,0)", nullable = false)
    private LineString geom;

    @Column(name = "length_m", nullable = false)
    private double lengthM;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    protected MapEdgeEntity() {
    }

    public UUID getEdgeId() {
        return edgeId;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getBuildJobId() {
        return buildJobId;
    }

    public UUID getFromNodeId() {
        return fromNodeId;
    }

    public UUID getToNodeId() {
        return toNodeId;
    }

    public EdgeType getEdgeType() {
        return edgeType;
    }

    public double getLengthM() {
        return lengthM;
    }
}
