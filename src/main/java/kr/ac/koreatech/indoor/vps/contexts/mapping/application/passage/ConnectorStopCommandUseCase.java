package kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage;

import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.FloorAreaEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.FloorAreaRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorRepository;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.repository.VerticalConnectorStopRepository;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "indoor.persistence", havingValue = "jpa", matchIfMissing = true)
public class ConnectorStopCommandUseCase {

    private final VerticalConnectorRepository connectorRepository;
    private final VerticalConnectorStopRepository stopRepository;
    private final FloorAreaRepository areaRepository;

    public ConnectorStopCommandUseCase(
            VerticalConnectorRepository connectorRepository,
            VerticalConnectorStopRepository stopRepository,
            FloorAreaRepository areaRepository
    ) {
        this.connectorRepository = connectorRepository;
        this.stopRepository = stopRepository;
        this.areaRepository = areaRepository;
    }

    @Transactional
    public ConnectorResult.Stop add(UUID connectorId, ConnectorStopCommand command) {
        VerticalConnectorEntity connector = connectorRepository.findById(connectorId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "connector not found: " + connectorId));
        UUID areaId = command.areaId().orElseThrow(() -> new ClientApiException(
                HttpStatus.BAD_REQUEST, "AREA_REQUIRED", "areaId is required for new stop"));
        FloorAreaEntity area = areaRepository.findById(areaId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId));
        VerticalConnectorStopEntity stop = VerticalConnectorStopEntity.create(
                UUID.randomUUID(),
                connector,
                area,
                null,
                command.routeNodeId().orElse(null)
        );
        return ConnectorResult.Stop.from(stopRepository.saveAndFlush(stop));
    }

    @Transactional
    public ConnectorResult.Stop update(UUID stopId, ConnectorStopCommand command) {
        VerticalConnectorStopEntity stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ClientApiException(
                        HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "stop not found: " + stopId));
        command.areaId().ifPresent(areaId -> {
            FloorAreaEntity area = areaRepository.findById(areaId)
                    .orElseThrow(() -> new ClientApiException(
                            HttpStatus.NOT_FOUND, "AREA_NOT_FOUND", "area not found: " + areaId));
            stop.changeArea(area);
        });
        if (command.detachRouteNode().orElse(false)) {
            stop.detachRouteNode();
        }
        command.routeNodeId().ifPresent(stop::attachRouteNode);
        return ConnectorResult.Stop.from(stopRepository.saveAndFlush(stop));
    }

    @Transactional
    public void remove(UUID stopId) {
        if (!stopRepository.existsById(stopId)) {
            throw new ClientApiException(
                    HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "stop not found: " + stopId);
        }
        stopRepository.deleteById(stopId);
    }
}
