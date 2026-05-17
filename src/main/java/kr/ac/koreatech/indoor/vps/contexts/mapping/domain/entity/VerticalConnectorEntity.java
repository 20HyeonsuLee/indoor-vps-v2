package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vertical_connector")
public class VerticalConnectorEntity {

    @Id
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingEntity building;

    @Column(name = "connector_type", nullable = false)
    private String connectorType;

    @Column(name = "connector_key", nullable = false)
    private String connectorKey;

    private String name;

    @Column(name = "is_mock", nullable = false)
    private boolean mock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VerticalConnectorEntity() {
    }

    public UUID getConnectorId() {
        return connectorId;
    }

    public BuildingEntity getBuilding() {
        return building;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getConnectorKey() {
        return connectorKey;
    }

    public String getName() {
        return name;
    }

    public boolean isMock() {
        return mock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
