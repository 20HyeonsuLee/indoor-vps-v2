package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity.DbEnums.NodeType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "map_node")
public class MapNodeEntity {
    @Id
    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "build_job_id", nullable = false)
    private UUID buildJobId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "node_type", nullable = false, columnDefinition = "node_type")
    private NodeType nodeType;

    @Column(columnDefinition = "geometry(PointZ,0)", nullable = false)
    private Point geom;

    private String label;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    protected MapNodeEntity() {
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getBuildJobId() {
        return buildJobId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Point getGeom() {
        return geom;
    }

    public String getLabel() {
        return label;
    }
}
