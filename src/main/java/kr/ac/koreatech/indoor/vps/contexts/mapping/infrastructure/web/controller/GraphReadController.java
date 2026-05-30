package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.EdgeDtos.*;
import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.NodeDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.EdgeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.graph.NodeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase.FloorMapResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "맵 그래프 (관리자)")
public class GraphReadController {

    private final GetFloorMapUseCase getFloorMapUseCase;

    public GraphReadController(GetFloorMapUseCase getFloorMapUseCase) {
        this.getFloorMapUseCase = getFloorMapUseCase;
    }

    @GetMapping("/floors/{floorId}/graph")
    public FloorGraphResponse getGraph(
            @PathVariable UUID floorId,
            @RequestParam(name = "areaId", required = false) UUID areaId
    ) {
        FloorMapResult result = getFloorMapUseCase.getFloorMap(floorId, Optional.ofNullable(areaId));
        List<NodeResponse> nodes = result.nodes().stream()
                .map(n -> NodeResponse.from(NodeResult.from(n)))
                .toList();
        List<EdgeResponse> edges = result.edges().stream()
                .map(e -> EdgeResponse.from(EdgeResult.from(e)))
                .toList();
        return new FloorGraphResponse(floorId, nodes, edges);
    }

    public record FloorGraphResponse(
            UUID floorId,
            List<NodeResponse> nodes,
            List<EdgeResponse> edges
    ) {
    }
}
