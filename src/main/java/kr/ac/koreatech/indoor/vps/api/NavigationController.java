package kr.ac.koreatech.indoor.vps.api;

import static kr.ac.koreatech.indoor.vps.api.dto.NavigationDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.navigation.NavigationApplicationService;
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
    private final NavigationApplicationService service;

    public NavigationController(NavigationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/floors/{floorId}/route")
    public Map<String, Object> getFloorRoute(
            @PathVariable UUID floorId,
            @RequestParam(name = "from") UUID fromNode,
            @RequestParam(name = "to") UUID toNode
    ) {
        return service.floorRoute(floorId, fromNode, toNode);
    }

    @PostMapping("/buildings/{buildingId}/pathfinding")
    public PathfindingResponse postPathfinding(
            @PathVariable UUID buildingId,
            @Valid @RequestBody PathfindingRequest request
    ) {
        return service.pathfinding(buildingId, request);
    }
}
