package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase.FloorMapResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetGraphUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetGraphUseCase.FloorPathResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.NavigationResponseMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "지도 데이터")
public class FloorMapController {
    private final GetGraphUseCase getGraphUseCase;
    private final GetFloorMapUseCase getFloorMapUseCase;
    private final NavigationResponseMapper responseMapper;

    public FloorMapController(
            GetGraphUseCase getGraphUseCase,
            GetFloorMapUseCase getFloorMapUseCase,
            NavigationResponseMapper responseMapper
    ) {
        this.getGraphUseCase = getGraphUseCase;
        this.getFloorMapUseCase = getFloorMapUseCase;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/floors/{floorId}/path")
    public FloorPathResponse getFloorPath(@PathVariable UUID floorId) {
        FloorPathResult result = getGraphUseCase.getFloorPath(floorId);
        return new FloorPathResponse(
                result.floorId(),
                result.scanId(),
                result.buildJobId(),
                result.nodes().stream().map(responseMapper::nodeMap).toList(),
                result.edges().stream().map(responseMapper::edgeMap).toList(),
                responseMapper.pathBounds(result.nodes()).orElse(null)
        );
    }

    @GetMapping("/floors/{floorId}/map")
    public ResponseEntity<FloorMapResponse> getFloorMap(
            @PathVariable UUID floorId,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        FloorMapResult result = getFloorMapUseCase.getFloorMap(floorId);
        String etag = "\"" + result.etag() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        FloorMapResponse response = new FloorMapResponse(
                result.floor().getFloorId(),
                result.floor().getBuilding().getBuildingId(),
                result.scanId(),
                result.floor().getLevel(),
                result.floor().getName(),
                result.buildJobId(),
                FloorMapCoordinateSystem.worldMeters(),
                responseMapper.floorMapBounds(result.nodes()),
                Map.of("type", "FeatureCollection", "features", List.of()),
                result.nodes().stream().map(responseMapper::floorMapNode).toList(),
                result.edges().stream().map(responseMapper::floorMapEdge).toList(),
                result.etag()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(response);
    }
}
