package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.BuildingDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BatchDeleteBuildingsUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingCreateCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingQueryService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingUpdateCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.building.BuildingUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "건물")
public class BuildingController {
    private final BuildingUseCase service;
    private final BuildingQueryService query;
    private final BatchDeleteBuildingsUseCase batchDelete;

    public BuildingController(
            BuildingUseCase service,
            BuildingQueryService query,
            BatchDeleteBuildingsUseCase batchDelete
    ) {
        this.service = service;
        this.query = query;
        this.batchDelete = batchDelete;
    }

    @GetMapping("/buildings")
    public List<BuildingResult.Summary> listBuildings(@RequestParam(name = "status", required = false) String status) {
        return query.listBuildings(status);
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResult.Summary createBuilding(@Valid @RequestBody BuildingCreateRequest request) {
        return service.createBuilding(new BuildingCreateCommand(
                request.name(), request.description(), request.latitude(), request.longitude()
        ));
    }

    @GetMapping("/buildings/{buildingId}")
    public BuildingResult.Detail getBuilding(@PathVariable UUID buildingId) {
        return query.getBuilding(buildingId);
    }

    @PutMapping("/buildings/{buildingId}")
    public BuildingResult.Summary updateBuilding(
            @PathVariable UUID buildingId,
            @RequestBody BuildingUpdateRequest request
    ) {
        return service.updateBuilding(buildingId, new BuildingUpdateCommand(
                request.name(), request.description(), request.latitude(), request.longitude()
        ));
    }

    @DeleteMapping("/buildings/{buildingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuilding(@PathVariable UUID buildingId) {
        service.deleteBuilding(buildingId);
    }

    @PatchMapping("/buildings/{buildingId}/status")
    public BuildingResult.Summary patchBuildingStatus(
            @PathVariable UUID buildingId,
            @Valid @RequestBody BuildingStatusRequest request
    ) {
        return service.patchStatus(buildingId, request.status());
    }

    @DeleteMapping("/buildings/batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuildings(@RequestBody List<UUID> buildingIds) {
        batchDelete.deleteAll(buildingIds);
    }
}
