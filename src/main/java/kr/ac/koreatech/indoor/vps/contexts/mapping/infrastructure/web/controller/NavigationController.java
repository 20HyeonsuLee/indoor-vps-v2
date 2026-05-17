package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.NavigationDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PathfindingResult.PathStep;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PlanRouteUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.PlanRouteUseCase.FloorRouteResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "길찾기")
public class NavigationController {
    private final PlanRouteUseCase planRouteUseCase;
    private final NavigationResponseMapper responseMapper;

    public NavigationController(PlanRouteUseCase planRouteUseCase, NavigationResponseMapper responseMapper) {
        this.planRouteUseCase = planRouteUseCase;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/floors/{floorId}/route")
    public Map<String, Object> getFloorRoute(
            @PathVariable UUID floorId,
            @RequestParam(name = "from") UUID fromNode,
            @RequestParam(name = "to") UUID toNode
    ) {
        FloorRouteResult result = planRouteUseCase.floorRoute(floorId, fromNode, toNode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("floorId", result.floorId());
        response.put("scanId", result.scanId());
        response.put("from", result.fromNode());
        response.put("to", result.toNode());
        response.put("totalDistance", result.totalDistance());
        response.put("nodes", result.nodes().stream().map(responseMapper::nodeMap).toList());
        response.put("edges", result.edges().stream().map(responseMapper::edgeMap).toList());
        return response;
    }

    @PostMapping("/buildings/{buildingId}/pathfinding")
    public PathfindingResponse postPathfinding(
            @PathVariable UUID buildingId,
            @Valid @RequestBody PathfindingRequest request
    ) {
        PathfindingResult result = planRouteUseCase.pathfinding(buildingId, new PathfindingCommand(
                request.startScanId(),
                request.startFloorLevel(),
                request.startX(),
                request.startY(),
                request.startZ(),
                request.destinationName()
        ));
        return toPathfindingResponse(result);
    }

    private PathfindingResponse toPathfindingResponse(PathfindingResult result) {
        return new PathfindingResponse(
                result.buildingId(),
                result.totalDistance(),
                result.estimatedTimeSeconds(),
                result.steps().stream().map(this::toPathStep).toList(),
                result.floorTransitions().stream().map(t -> new FloorTransitionResponse(
                        t.fromFloorLevel(), t.toFloorLevel(), t.connectorType(), t.connectorKey()
                )).toList(),
                result.routeMetadata()
        );
    }

    private PathStepResponse toPathStep(PathStep step) {
        return new PathStepResponse(
                step.stepNumber(),
                step.floorLevel(),
                new RoutePosition(
                        step.position().x(),
                        step.position().y(),
                        step.position().z(),
                        step.position().floorLevel()
                ),
                step.instruction(),
                step.nodeId()
        );
    }
}
