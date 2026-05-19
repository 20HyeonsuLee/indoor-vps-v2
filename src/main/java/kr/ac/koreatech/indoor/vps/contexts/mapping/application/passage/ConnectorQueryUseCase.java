package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ConnectorQueryUseCase {

    private final VerticalConnectorRepository connectorRepository;
    private final VerticalConnectorStopRepository stopRepository;
    private final BuildingQueryService buildingQuery;

    public ConnectorQueryUseCase(
            VerticalConnectorRepository connectorRepository,
            VerticalConnectorStopRepository stopRepository,
            BuildingQueryService buildingQuery
    ) {
        this.connectorRepository = connectorRepository;
        this.stopRepository = stopRepository;
        this.buildingQuery = buildingQuery;
    }

    public List<ConnectorResult> listForBuilding(UUID buildingId) {
        buildingQuery.requireBuilding(buildingId);
        List<VerticalConnectorStopEntity> stops = stopRepository.findByConnector_Building_BuildingId(buildingId);
        return stops.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getConnector().getConnectorId()))
                .entrySet().stream()
                .map(entry -> {
                    VerticalConnectorEntity connector = entry.getValue().get(0).getConnector();
                    return ConnectorResult.from(connector, entry.getValue());
                })
                .toList();
    }

    public ConnectorResult get(UUID connectorId) {
        VerticalConnectorEntity connector = connectorRepository.findById(connectorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "connector not found: " + connectorId));
        List<VerticalConnectorStopEntity> stops = stopRepository
                .findByConnector_Building_BuildingId(connector.getBuilding().getBuildingId())
                .stream()
                .filter(s -> s.getConnector().getConnectorId().equals(connectorId))
                .toList();
        return ConnectorResult.from(connector, stops);
    }
}
