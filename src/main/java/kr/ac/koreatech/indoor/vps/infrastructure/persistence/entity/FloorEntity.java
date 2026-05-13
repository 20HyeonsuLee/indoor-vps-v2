package kr.ac.koreatech.indoor.vps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "building_floor")
public class FloorEntity {
    @Id
    @Column(name = "floor_id", nullable = false)
    private UUID floorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingEntity building;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int level;

    private Double height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FloorEntity() {
    }

    public FloorEntity(BuildingEntity building, String name, int level, Double height) {
        this.building = building;
        this.name = name;
        this.level = level;
        this.height = height;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (floorId == null) {
            floorId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getFloorId() {
        return floorId;
    }

    public BuildingEntity getBuilding() {
        return building;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
