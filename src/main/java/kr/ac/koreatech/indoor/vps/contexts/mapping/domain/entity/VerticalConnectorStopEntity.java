package kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "vertical_connector_stop")
public class VerticalConnectorStopEntity {

    @Id
    @Column(name = "connector_stop_id", nullable = false)
    private UUID connectorStopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connector_id", nullable = false)
    private VerticalConnectorEntity connector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private FloorAreaEntity area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_canonical_id")
    private PoiCanonicalEntity poiCanonical;

    @Column(name = "route_node_id")
    private UUID routeNodeId;

    protected VerticalConnectorStopEntity() {
    }

    public static VerticalConnectorStopEntity create(
            UUID stopId,
            VerticalConnectorEntity connector,
            FloorAreaEntity area,
            PoiCanonicalEntity poiCanonical,
            UUID routeNodeId
    ) {
        VerticalConnectorStopEntity entity = new VerticalConnectorStopEntity();
        entity.connectorStopId = stopId;
        entity.connector = connector;
        entity.area = area;
        entity.poiCanonical = poiCanonical;
        entity.routeNodeId = routeNodeId;
        return entity;
    }

    public UUID getConnectorStopId() {
        return connectorStopId;
    }

    public VerticalConnectorEntity getConnector() {
        return connector;
    }

    public FloorAreaEntity getArea() {
        return area;
    }

    public PoiCanonicalEntity getPoiCanonical() {
        return poiCanonical;
    }

    public UUID getRouteNodeId() {
        return routeNodeId;
    }

    public void retarget(PoiCanonicalEntity newPoi, UUID newRouteNodeId) {
        this.poiCanonical = newPoi;
        this.routeNodeId = newRouteNodeId;
    }
}
