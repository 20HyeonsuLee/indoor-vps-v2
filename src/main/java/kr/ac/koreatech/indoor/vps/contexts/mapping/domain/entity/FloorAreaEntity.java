package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "floor_area")
public class FloorAreaEntity {

    @Id
    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private FloorEntity floor;

    @Column(name = "area_index", nullable = false)
    private int areaIndex;

    @Column(nullable = false)
    private String label;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FloorAreaEntity() {
    }

    public static FloorAreaEntity createDefault(FloorEntity floor) {
        FloorAreaEntity area = new FloorAreaEntity();
        area.floor = floor;
        area.areaIndex = 0;
        area.label = "기본 구역";
        area.isDefault = true;
        return area;
    }

    public static FloorAreaEntity create(FloorEntity floor, int areaIndex, String label) {
        FloorAreaEntity area = new FloorAreaEntity();
        area.floor = floor;
        area.areaIndex = areaIndex;
        area.label = label != null ? label : "Area " + (areaIndex + 1);
        area.isDefault = false;
        return area;
    }

    @PrePersist
    void prePersist() {
        if (areaId == null) {
            areaId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void rename(String newLabel) {
        if (newLabel != null && !newLabel.isBlank()) {
            this.label = newLabel;
        }
    }

    public UUID getAreaId() {
        return areaId;
    }

    public FloorEntity getFloor() {
        return floor;
    }

    public int getAreaIndex() {
        return areaIndex;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
