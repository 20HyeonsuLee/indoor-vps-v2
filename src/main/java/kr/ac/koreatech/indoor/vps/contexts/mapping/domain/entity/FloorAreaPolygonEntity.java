package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "floor_area_polygon")
public class FloorAreaPolygonEntity {

    @Id
    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(name = "build_job_id", nullable = false)
    private UUID buildJobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floor;

    @Column(name = "mark_session_id", nullable = false)
    private String markSessionId;

    @Column(columnDefinition = "geometry(PolygonZ,0)")
    private Polygon polygon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_mark_ids", columnDefinition = "jsonb")
    private List<Long> sourceMarkIds;

    protected FloorAreaPolygonEntity() {
    }

    public static FloorAreaPolygonEntity create(
            UUID areaId,
            UUID scanId,
            UUID buildJobId,
            FloorEntity floor,
            String markSessionId,
            Polygon polygon,
            List<Long> sourceMarkIds
    ) {
        FloorAreaPolygonEntity entity = new FloorAreaPolygonEntity();
        entity.areaId = areaId;
        entity.scanId = scanId;
        entity.buildJobId = buildJobId;
        entity.floor = floor;
        entity.markSessionId = markSessionId;
        entity.polygon = polygon;
        entity.sourceMarkIds = sourceMarkIds;
        return entity;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getBuildJobId() {
        return buildJobId;
    }

    public FloorEntity getFloor() {
        return floor;
    }

    public String getMarkSessionId() {
        return markSessionId;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    public List<Long> getSourceMarkIds() {
        return sourceMarkIds;
    }
}
