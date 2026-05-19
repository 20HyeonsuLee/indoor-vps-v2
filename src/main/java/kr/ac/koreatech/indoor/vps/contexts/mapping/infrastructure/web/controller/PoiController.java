package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.PoiManageDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.CreateManualPoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.CreatePoiCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.DeletePoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.PoiResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.PoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.UpdatePoiCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.UpdatePoiUseCase;
import kr.ac.koreatech.indoor.vps.contexts.mapping.domain.entity.PoiCanonicalEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "POI")
public class PoiController {

    private final PoiUseCase queryUseCase;
    private final PoiManageRouter manage;

    public PoiController(PoiUseCase queryUseCase, PoiManageRouter manage) {
        this.queryUseCase = queryUseCase;
        this.manage = manage;
    }

    @GetMapping("/buildings/{buildingId}/pois")
    public List<PoiResult> listPois(@PathVariable UUID buildingId) {
        return queryUseCase.listPois(buildingId);
    }

    @GetMapping("/buildings/{buildingId}/pois/search")
    public List<PoiResult> searchPois(
            @PathVariable UUID buildingId,
            @RequestParam(name = "query", required = false) String query
    ) {
        return queryUseCase.searchPois(buildingId, query);
    }

    @PostMapping("/buildings/{buildingId}/pois")
    public PoiResult create(@PathVariable UUID buildingId, @Valid @RequestBody PoiCreateRequest body) {
        return toResult(manage.create(buildingId, toCreateCommand(body)));
    }

    @PutMapping("/pois/{poiId}")
    public PoiResult update(@PathVariable UUID poiId, @RequestBody PoiUpdateRequest body) {
        return toResult(manage.update(poiId, toUpdateCommand(body)));
    }

    @PutMapping("/pois/{poiId}/route-node")
    public PoiResult attach(@PathVariable UUID poiId, @Valid @RequestBody PoiAttachRequest body) {
        UpdatePoiCommand command = new UpdatePoiCommand(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(body.routeNodeId()), Optional.empty(), Optional.empty()
        );
        return toResult(manage.update(poiId, command));
    }

    @DeleteMapping("/pois/{poiId}")
    public ResponseEntity<Void> delete(@PathVariable UUID poiId) {
        manage.delete(poiId);
        return ResponseEntity.noContent().build();
    }

    private CreatePoiCommand toCreateCommand(PoiCreateRequest body) {
        return new CreatePoiCommand(
                body.areaId(), body.name(), body.category(),
                body.x(), body.y(), body.z(),
                Optional.ofNullable(body.displayX()),
                Optional.ofNullable(body.displayY()),
                Optional.ofNullable(body.displayZ()),
                Optional.ofNullable(body.routeNodeId())
        );
    }

    private UpdatePoiCommand toUpdateCommand(PoiUpdateRequest body) {
        return new UpdatePoiCommand(
                Optional.ofNullable(body.name()),
                Optional.ofNullable(body.category()),
                Optional.ofNullable(body.label()),
                Optional.ofNullable(body.x()),
                Optional.ofNullable(body.y()),
                Optional.ofNullable(body.z()),
                Optional.ofNullable(body.displayX()),
                Optional.ofNullable(body.displayY()),
                Optional.ofNullable(body.displayZ()),
                Optional.ofNullable(body.routeNodeId()),
                Optional.ofNullable(body.detachRouteNode()),
                Optional.ofNullable(body.markReviewed())
        );
    }

    private PoiResult toResult(PoiCanonicalEntity poi) {
        Point geom = poi.getDisplayPoint() != null ? poi.getDisplayPoint() : poi.getWorldPose();
        java.util.Map<String, Double> display = geom == null ? null : java.util.Map.of(
                "x", geom.getX(),
                "y", geom.getY(),
                "z", zOrZero(geom)
        );
        return new PoiResult(
                poi.getCanonicalId(),
                poi.getBuilding() == null ? null : poi.getBuilding().getBuildingId(),
                poi.getFloor() == null ? null : poi.getFloor().getFloorId(),
                poi.getName(),
                poi.getLabel(),
                poi.getCategory(),
                poi.getRouteNodeId(),
                display,
                poi.isNeedsReview(),
                poi.getLlmConfidence()
        );
    }

    private double zOrZero(Point point) {
        Coordinate c = point.getCoordinate();
        return Double.isNaN(c.getZ()) ? 0.0 : c.getZ();
    }
}
