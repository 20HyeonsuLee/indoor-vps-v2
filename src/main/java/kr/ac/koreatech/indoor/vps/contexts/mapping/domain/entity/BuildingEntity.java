package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "building")
public class BuildingEntity {
    @Id
    @Column(name = "building_id", nullable = false)
    private UUID buildingId;

    @Column(nullable = false)
    private String name;

    private String description;
    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuildingEntity() {
    }

    public BuildingEntity(String name, String description, Double latitude, Double longitude) {
        this.name = name;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (buildingId == null) {
            buildingId = UUID.randomUUID();
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

    public UUID getBuildingId() {
        return buildingId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getStatus() {
        return status;
    }

    public void updateProfile(String name, String description, Double latitude, Double longitude) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
    }

    public void changeStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
