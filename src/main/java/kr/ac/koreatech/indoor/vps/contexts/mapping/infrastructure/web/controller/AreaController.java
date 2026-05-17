package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.AreaDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.area.AreaResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.area.CreateAreaCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.area.FloorAreaUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "구역")
public class AreaController {

    private final FloorAreaUseCase areaUseCase;

    public AreaController(FloorAreaUseCase areaUseCase) {
        this.areaUseCase = areaUseCase;
    }

    @GetMapping("/floors/{floorId}/areas")
    public List<AreaResult> listAreas(@PathVariable UUID floorId) {
        return areaUseCase.listAreas(floorId);
    }

    @PostMapping("/floors/{floorId}/areas")
    @ResponseStatus(HttpStatus.CREATED)
    public AreaResult createArea(@PathVariable UUID floorId, @RequestBody CreateAreaRequest request) {
        return areaUseCase.createArea(floorId, new CreateAreaCommand(request.label()));
    }
}
