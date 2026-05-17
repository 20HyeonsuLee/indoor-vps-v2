package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.FloorDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorCreateCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUpdateCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.floor.FloorUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "층")
public class FloorController {
    private final FloorUseCase service;
    private final FloorQueryService query;

    public FloorController(FloorUseCase service, FloorQueryService query) {
        this.service = service;
        this.query = query;
    }

    @GetMapping("/buildings/{buildingId}/floors")
    public List<FloorResult> listFloors(@PathVariable UUID buildingId) {
        return query.listFloors(buildingId);
    }

    @PostMapping("/buildings/{buildingId}/floors")
    @ResponseStatus(HttpStatus.CREATED)
    public FloorResult createFloor(
            @PathVariable UUID buildingId,
            @Valid @RequestBody FloorCreateRequest request
    ) {
        return service.createFloor(buildingId, new FloorCreateCommand(request.name(), request.level(), request.height()));
    }

    @GetMapping("/floors/{floorId}")
    public FloorResult getFloor(@PathVariable UUID floorId) {
        return query.getFloor(floorId);
    }

    @PutMapping("/floors/{floorId}")
    public FloorResult updateFloor(@PathVariable UUID floorId, @RequestBody FloorUpdateRequest request) {
        return service.updateFloor(floorId, new FloorUpdateCommand(request.name(), request.height()));
    }

    @DeleteMapping("/floors/{floorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFloor(@PathVariable UUID floorId) {
        service.deleteFloor(floorId);
    }
}
