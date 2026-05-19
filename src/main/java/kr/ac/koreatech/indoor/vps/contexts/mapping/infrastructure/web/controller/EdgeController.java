package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.EdgeDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.ClearManualGraphUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.CreateEdgeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.CreateManualEdgeUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.DeleteEdgeUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.UpdateEdgeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.UpdateEdgeUseCase;
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
@Tag(name = "맵 엣지")
public class EdgeController {

    private final CreateManualEdgeUseCase createUseCase;
    private final UpdateEdgeUseCase updateUseCase;
    private final DeleteEdgeUseCase deleteUseCase;
    private final ClearManualGraphUseCase clearUseCase;

    public EdgeController(
            CreateManualEdgeUseCase createUseCase,
            UpdateEdgeUseCase updateUseCase,
            DeleteEdgeUseCase deleteUseCase,
            ClearManualGraphUseCase clearUseCase
    ) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.clearUseCase = clearUseCase;
    }

    @PostMapping("/areas/{areaId}/edges")
    public EdgeResponse create(@PathVariable UUID areaId, @Valid @RequestBody EdgeCreateRequest body) {
        return EdgeResponse.from(createUseCase.create(areaId, toCreateCommand(body)));
    }

    @PutMapping("/edges/{edgeId}")
    public EdgeResponse update(@PathVariable UUID edgeId, @RequestBody EdgeUpdateRequest body) {
        return EdgeResponse.from(updateUseCase.update(edgeId, new UpdateEdgeCommand(Optional.ofNullable(body.edgeType()))));
    }

    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID edgeId) {
        deleteUseCase.delete(edgeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/areas/{areaId}/graph/manual")
    public ResponseEntity<Void> clearManual(@PathVariable UUID areaId) {
        clearUseCase.clear(areaId);
        return ResponseEntity.noContent().build();
    }

    private CreateEdgeCommand toCreateCommand(EdgeCreateRequest body) {
        return new CreateEdgeCommand(
                body.fromNodeId(),
                body.toNodeId(),
                Optional.ofNullable(body.edgeType())
        );
    }
}
