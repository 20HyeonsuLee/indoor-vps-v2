package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetGraphUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetGraphUseCase.FloorPathResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "지도 데이터")
public class FloorPathController {
    private final GetGraphUseCase getGraphUseCase;
    private final RouteResponseMapper routeMapper;

    public FloorPathController(GetGraphUseCase getGraphUseCase, RouteResponseMapper routeMapper) {
        this.getGraphUseCase = getGraphUseCase;
        this.routeMapper = routeMapper;
    }

    @GetMapping("/floors/{floorId}/path")
    public FloorPathResponse getFloorPath(
            @PathVariable UUID floorId,
            @RequestParam(name = "areaId", required = false) UUID areaId
    ) {
        FloorPathResult result = getGraphUseCase.getFloorPath(floorId, Optional.ofNullable(areaId));
        return new FloorPathResponse(
                result.floorId(),
                result.scanId(),
                result.buildJobId(),
                result.nodes().stream().map(routeMapper::nodeMap).toList(),
                result.edges().stream().map(routeMapper::edgeMap).toList(),
                routeMapper.pathBounds(result.nodes()).orElse(null)
        );
    }
}
