package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.BuildingDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

    public BuildingController(BuildingUseCase service) {
        this.service = service;
    }

    @GetMapping("/buildings")
    public List<BuildingResponse> listBuildings(@RequestParam(name = "status", required = false) String status) {
        return service.listBuildings(status);
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResponse createBuilding(@Valid @RequestBody BuildingCreateRequest request) {
        return service.createBuilding(request);
    }

    @GetMapping("/buildings/{buildingId}")
    public BuildingDetailResponse getBuilding(@PathVariable UUID buildingId) {
        return service.getBuilding(buildingId);
    }

    @PutMapping("/buildings/{buildingId}")
    public BuildingResponse updateBuilding(
            @PathVariable UUID buildingId,
            @RequestBody BuildingUpdateRequest request
    ) {
        return service.updateBuilding(buildingId, request);
    }

    @DeleteMapping("/buildings/{buildingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuilding(@PathVariable UUID buildingId) {
        service.deleteBuilding(buildingId);
    }

    @PatchMapping("/buildings/{buildingId}/status")
    public BuildingResponse patchBuildingStatus(
            @PathVariable UUID buildingId,
            @Valid @RequestBody BuildingStatusRequest request
    ) {
        return service.patchStatus(buildingId, request);
    }
}
