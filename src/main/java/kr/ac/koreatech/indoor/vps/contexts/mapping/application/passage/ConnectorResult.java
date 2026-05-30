package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;

public record ConnectorResult(
        UUID connectorId,
        UUID buildingId,
        String connectorType,
        String connectorKey,
        String name,
        boolean mock,
        List<Stop> stops
) {
    public record Stop(
            UUID stopId,
            UUID areaId,
            String areaLabel,
            UUID floorId,
            int floorLevel,
            UUID routeNodeId
    ) {
        public static Stop from(VerticalConnectorStopEntity stop) {
            return new Stop(
                    stop.getConnectorStopId(),
                    stop.getArea().getAreaId(),
                    stop.getArea().getLabel(),
                    stop.getArea().getFloor().getFloorId(),
                    stop.getArea().getFloor().getLevel(),
                    stop.getRouteNodeId()
            );
        }
    }

    public static ConnectorResult from(VerticalConnectorEntity connector, List<VerticalConnectorStopEntity> stops) {
        return new ConnectorResult(
                connector.getConnectorId(),
                connector.getBuilding().getBuildingId(),
                connector.getConnectorType(),
                connector.getConnectorKey(),
                connector.getName(),
                connector.isMock(),
                stops.stream().map(Stop::from).toList()
        );
    }
}
