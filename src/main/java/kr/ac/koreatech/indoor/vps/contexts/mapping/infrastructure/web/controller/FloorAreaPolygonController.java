package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.PolygonDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.CreateManualPolygonUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.DeletePolygonUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.PolygonCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.PolygonQueryUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.PolygonResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.UpdatePolygonUseCase;
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
@Tag(name = "Area Polygon (Corner)")
public class FloorAreaPolygonController {

    private final PolygonQueryUseCase queryUseCase;
    private final CreateManualPolygonUseCase createUseCase;
    private final UpdatePolygonUseCase updateUseCase;
    private final DeletePolygonUseCase deleteUseCase;

    public FloorAreaPolygonController(
            PolygonQueryUseCase queryUseCase,
            CreateManualPolygonUseCase createUseCase,
            UpdatePolygonUseCase updateUseCase,
            DeletePolygonUseCase deleteUseCase
    ) {
        this.queryUseCase = queryUseCase;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @GetMapping("/areas/{areaId}/polygons")
    public List<PolygonResult> list(@PathVariable UUID areaId) {
        return queryUseCase.listByArea(areaId);
    }

    @PostMapping("/areas/{areaId}/polygons")
    public PolygonResult create(@PathVariable UUID areaId, @Valid @RequestBody PolygonRequest body) {
        return createUseCase.create(areaId, toCommand(body));
    }

    @PutMapping("/polygons/{polygonId}")
    public PolygonResult update(@PathVariable UUID polygonId, @Valid @RequestBody PolygonRequest body) {
        return updateUseCase.update(polygonId, toCommand(body));
    }

    @DeleteMapping("/polygons/{polygonId}")
    public ResponseEntity<Void> delete(@PathVariable UUID polygonId) {
        deleteUseCase.delete(polygonId);
        return ResponseEntity.noContent().build();
    }

    private PolygonCommand toCommand(PolygonRequest body) {
        List<PolygonCommand.Vertex> vertices = body.exterior().stream()
                .map(v -> new PolygonCommand.Vertex(v.x(), v.y(), v.z()))
                .toList();
        return new PolygonCommand(vertices);
    }
}
