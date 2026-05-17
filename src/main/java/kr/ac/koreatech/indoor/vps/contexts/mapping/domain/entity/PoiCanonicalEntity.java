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
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "poi_canonical")
public class PoiCanonicalEntity {
    @Id
    @Column(name = "canonical_id", nullable = false)
    private UUID canonicalId;

    private String label;

    @Column(name = "scan_id")
    private UUID scanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private BuildingEntity building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private FloorAreaEntity area;

    @Column(nullable = false)
    private String category;

    private String name;

    @Column(name = "world_pose", columnDefinition = "geometry(PointZ,0)")
    private Point worldPose;

    @Column(name = "display_point", columnDefinition = "geometry(PointZ,0)")
    private Point displayPoint;

    @Column(name = "route_node_id")
    private UUID routeNodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_mark_ids", columnDefinition = "jsonb")
    private List<Long> sourceMarkIds;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "llm_confidence")
    private Double llmConfidence;

    protected PoiCanonicalEntity() {
    }

    public static PoiCanonicalEntity createFromMark(
            UUID canonicalId,
            UUID scanId,
            BuildingEntity building,
            FloorEntity floor,
            FloorAreaEntity area,
            String label,
            String category,
            Point worldPose,
            UUID routeNodeId,
            List<Long> sourceMarkIds
    ) {
        PoiCanonicalEntity entity = new PoiCanonicalEntity();
        entity.canonicalId = canonicalId;
        entity.scanId = scanId;
        entity.building = building;
        entity.floor = floor;
        entity.area = area;
        entity.label = label;
        entity.name = label;
        entity.category = category;
        entity.worldPose = worldPose;
        entity.routeNodeId = routeNodeId;
        entity.sourceMarkIds = sourceMarkIds;
        entity.needsReview = false;
        return entity;
    }

    public UUID getCanonicalId() {
        return canonicalId;
    }

    public String getLabel() {
        return label;
    }

    public BuildingEntity getBuilding() {
        return building;
    }

    public FloorEntity getFloor() {
        return floor;
    }

    public FloorAreaEntity getArea() {
        return area;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public Point getWorldPose() {
        return worldPose;
    }

    public Point getDisplayPoint() {
        return displayPoint;
    }

    public UUID getRouteNodeId() {
        return routeNodeId;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public List<Long> getSourceMarkIds() {
        return sourceMarkIds;
    }

    public Double getLlmConfidence() {
        return llmConfidence;
    }
}
