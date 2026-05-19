package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.ConnectorDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorCommandUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorCreateCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorQueryUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorStopCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorStopCommandUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.ConnectorUpdateCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "수직 연결")
public class VerticalConnectorController {

    private final ConnectorQueryUseCase queryUseCase;
    private final ConnectorCommandUseCase commandUseCase;
    private final ConnectorStopCommandUseCase stopUseCase;

    public VerticalConnectorController(
            ConnectorQueryUseCase queryUseCase,
            ConnectorCommandUseCase commandUseCase,
            ConnectorStopCommandUseCase stopUseCase
    ) {
        this.queryUseCase = queryUseCase;
        this.commandUseCase = commandUseCase;
        this.stopUseCase = stopUseCase;
    }

    @GetMapping("/buildings/{buildingId}/connectors")
    public List<ConnectorResult> list(@PathVariable UUID buildingId) {
        return queryUseCase.listForBuilding(buildingId);
    }

    @GetMapping("/connectors/{connectorId}")
    public ConnectorResult get(@PathVariable UUID connectorId) {
        return queryUseCase.get(connectorId);
    }

    @PostMapping("/buildings/{buildingId}/connectors")
    public ConnectorResult create(@PathVariable UUID buildingId, @Valid @RequestBody ConnectorCreateRequest body) {
        return commandUseCase.create(buildingId, new ConnectorCreateCommand(
                body.connectorType(), body.connectorKey(), body.name()));
    }

    @PutMapping("/connectors/{connectorId}")
    public ConnectorResult update(@PathVariable UUID connectorId, @RequestBody ConnectorUpdateRequest body) {
        ConnectorUpdateCommand command = new ConnectorUpdateCommand(
                Optional.ofNullable(body.connectorType()),
                Optional.ofNullable(body.connectorKey()),
                Optional.ofNullable(body.name()),
                Optional.ofNullable(body.mock())
        );
        return commandUseCase.update(connectorId, command);
    }

    @DeleteMapping("/connectors/{connectorId}")
    public ResponseEntity<Void> delete(@PathVariable UUID connectorId) {
        commandUseCase.delete(connectorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connectors/{connectorId}/stops")
    public ConnectorResult.Stop addStop(@PathVariable UUID connectorId, @RequestBody ConnectorStopRequest body) {
        return stopUseCase.add(connectorId, toCommand(body));
    }

    @PutMapping("/connector-stops/{stopId}")
    public ConnectorResult.Stop updateStop(@PathVariable UUID stopId, @RequestBody ConnectorStopRequest body) {
        return stopUseCase.update(stopId, toCommand(body));
    }

    @DeleteMapping("/connector-stops/{stopId}")
    public ResponseEntity<Void> removeStop(@PathVariable UUID stopId) {
        stopUseCase.remove(stopId);
        return ResponseEntity.noContent().build();
    }

    private ConnectorStopCommand toCommand(ConnectorStopRequest body) {
        return new ConnectorStopCommand(
                Optional.ofNullable(body.areaId()),
                Optional.ofNullable(body.routeNodeId()),
                Optional.ofNullable(body.detachRouteNode())
        );
    }
}
