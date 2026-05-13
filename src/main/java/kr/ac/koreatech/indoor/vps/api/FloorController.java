package kr.ac.koreatech.indoor.vps.api;

import static kr.ac.koreatech.indoor.vps.api.dto.FloorDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.floor.FloorApplicationService;
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
    private final FloorApplicationService service;

    public FloorController(FloorApplicationService service) {
        this.service = service;
    }

    @GetMapping("/buildings/{buildingId}/floors")
    public List<FloorResponse> listFloors(@PathVariable UUID buildingId) {
        return service.listFloors(buildingId);
    }

    @PostMapping("/buildings/{buildingId}/floors")
    @ResponseStatus(HttpStatus.CREATED)
    public FloorResponse createFloor(
            @PathVariable UUID buildingId,
            @Valid @RequestBody FloorCreateRequest request
    ) {
        return service.createFloor(buildingId, request);
    }

    @GetMapping("/floors/{floorId}")
    public FloorResponse getFloor(@PathVariable UUID floorId) {
        return service.getFloor(floorId);
    }

    @PutMapping("/floors/{floorId}")
    public FloorResponse updateFloor(@PathVariable UUID floorId, @RequestBody FloorUpdateRequest request) {
        return service.updateFloor(floorId, request);
    }

    @DeleteMapping("/floors/{floorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFloor(@PathVariable UUID floorId) {
        service.deleteFloor(floorId);
    }
}
