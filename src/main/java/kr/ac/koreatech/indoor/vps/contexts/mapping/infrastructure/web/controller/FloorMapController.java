package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.MapDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.navigation.GetFloorMapUseCase.FloorMapResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "지도 데이터")
public class FloorMapController {
    private final GetFloorMapUseCase getFloorMapUseCase;
    private final FloorMapResponseMapper floorMapMapper;

    public FloorMapController(
            GetFloorMapUseCase getFloorMapUseCase,
            FloorMapResponseMapper floorMapMapper
    ) {
        this.getFloorMapUseCase = getFloorMapUseCase;
        this.floorMapMapper = floorMapMapper;
    }

    @GetMapping("/floors/{floorId}/map")
    public ResponseEntity<FloorMapResponse> getFloorMap(
            @PathVariable UUID floorId,
            @RequestParam(name = "areaId", required = false) UUID areaId,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        FloorMapResult result = getFloorMapUseCase.getFloorMap(floorId, Optional.ofNullable(areaId));
        String etag = "\"" + result.etag() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        java.util.Map<UUID, kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity> poiByRoute =
                new java.util.HashMap<>();
        for (var p : result.pois()) {
            if (p.getRouteNodeId() != null) {
                poiByRoute.put(p.getRouteNodeId(), p);
            }
        }
        java.util.Map<UUID, kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.VerticalConnectorStopEntity> stopByRoute =
                new java.util.HashMap<>();
        for (var s : result.stopsInArea()) {
            if (s.getRouteNodeId() != null) {
                stopByRoute.put(s.getRouteNodeId(), s);
            }
        }
        java.util.Map<UUID, kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.MapNodeEntity> nodeById =
                new java.util.HashMap<>();
        for (var n : result.nodes()) {
            nodeById.put(n.getNodeId(), n);
        }
        FloorMapResponse response = new FloorMapResponse(
                result.floor().getFloorId(),
                result.floor().getBuilding().getBuildingId(),
                result.scanId(),
                result.floor().getLevel(),
                result.floor().getName(),
                result.buildJobId(),
                FloorMapCoordinateSystem.worldMeters(),
                floorMapMapper.floorMapBounds(result.nodes()),
                floorMapMapper.polygonFeatureCollection(result.polygons()),
                result.nodes().stream()
                        .map(n -> floorMapMapper.floorMapNode(n, poiByRoute, stopByRoute))
                        .toList(),
                result.edges().stream().map(floorMapMapper::floorMapEdge).toList(),
                floorMapMapper.destinations(result.pois(), stopByRoute),
                floorMapMapper.connectors(result.stopsInArea(), result.stopsInBuilding(), nodeById),
                result.etag()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(response);
    }
}
