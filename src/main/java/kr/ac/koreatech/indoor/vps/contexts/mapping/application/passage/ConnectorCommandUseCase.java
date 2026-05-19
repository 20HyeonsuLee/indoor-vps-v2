package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.BuildingEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ConnectorCommandUseCase {

    private final VerticalConnectorRepository connectorRepository;
    private final VerticalConnectorStopRepository stopRepository;
    private final BuildingQueryService buildingQuery;

    public ConnectorCommandUseCase(
            VerticalConnectorRepository connectorRepository,
            VerticalConnectorStopRepository stopRepository,
            BuildingQueryService buildingQuery
    ) {
        this.connectorRepository = connectorRepository;
        this.stopRepository = stopRepository;
        this.buildingQuery = buildingQuery;
    }

    @Transactional
    public ConnectorResult create(UUID buildingId, ConnectorCreateCommand command) {
        BuildingEntity building = buildingQuery.requireBuilding(buildingId);
        VerticalConnectorEntity connector = VerticalConnectorEntity.create(
                UUID.randomUUID(),
                building,
                command.connectorType(),
                command.connectorKey(),
                command.name()
        );
        VerticalConnectorEntity saved = connectorRepository.saveAndFlush(connector);
        return ConnectorResult.from(saved, java.util.List.of());
    }

    @Transactional
    public ConnectorResult update(UUID connectorId, ConnectorUpdateCommand command) {
        VerticalConnectorEntity connector = requireConnector(connectorId);
        command.connectorType().ifPresent(connector::changeType);
        command.connectorKey().ifPresent(connector::changeKey);
        command.name().ifPresent(connector::rename);
        command.mock().ifPresent(connector::setMock);
        return ConnectorResult.from(connectorRepository.saveAndFlush(connector),
                stopRepository.findByConnector_Building_BuildingId(connector.getBuilding().getBuildingId())
                        .stream().filter(s -> s.getConnector().getConnectorId().equals(connectorId)).toList());
    }

    @Transactional
    public void delete(UUID connectorId) {
        if (!connectorRepository.existsById(connectorId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "connector not found: " + connectorId);
        }
        connectorRepository.deleteById(connectorId);
    }

    private VerticalConnectorEntity requireConnector(UUID connectorId) {
        return connectorRepository.findById(connectorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "connector not found: " + connectorId));
    }
}
