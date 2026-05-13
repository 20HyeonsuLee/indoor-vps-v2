package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
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

    @Column(nullable = false)
    private String category;

    private String name;

    @Column(name = "world_pose", columnDefinition = "geometry(PointZ,0)")
    private Point worldPose;

    @Column(name = "display_point", columnDefinition = "geometry(PointZ,0)")
    private Point displayPoint;

    @Column(name = "route_node_id")
    private UUID routeNodeId;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "llm_confidence")
    private Double llmConfidence;

    protected PoiCanonicalEntity() {
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

    public Double getLlmConfidence() {
        return llmConfidence;
    }
}
