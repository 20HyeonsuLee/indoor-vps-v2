package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.NodeDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.CreateManualNodeUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.CreateNodeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.DeleteNodeUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.UpdateNodeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.UpdateNodeUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "맵 노드")
public class NodeController {

    private final CreateManualNodeUseCase createUseCase;
    private final UpdateNodeUseCase updateUseCase;
    private final DeleteNodeUseCase deleteUseCase;

    public NodeController(
            CreateManualNodeUseCase createUseCase,
            UpdateNodeUseCase updateUseCase,
            DeleteNodeUseCase deleteUseCase
    ) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping("/areas/{areaId}/nodes")
    public NodeResponse create(@PathVariable UUID areaId, @Valid @RequestBody NodeCreateRequest body) {
        return NodeResponse.from(createUseCase.create(areaId, toCreateCommand(body)));
    }

    @PutMapping("/nodes/{nodeId}")
    public NodeResponse update(@PathVariable UUID nodeId, @RequestBody NodeUpdateRequest body) {
        return NodeResponse.from(updateUseCase.update(nodeId, toUpdateCommand(body)));
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID nodeId) {
        deleteUseCase.delete(nodeId);
        return ResponseEntity.noContent().build();
    }

    private CreateNodeCommand toCreateCommand(NodeCreateRequest body) {
        return new CreateNodeCommand(body.x(), body.y(), body.z(), body.nodeType(), body.label());
    }

    private UpdateNodeCommand toUpdateCommand(NodeUpdateRequest body) {
        return new UpdateNodeCommand(
                Optional.ofNullable(body.x()),
                Optional.ofNullable(body.y()),
                Optional.ofNullable(body.z()),
                Optional.ofNullable(body.nodeType()),
                Optional.ofNullable(body.label())
        );
    }
}
