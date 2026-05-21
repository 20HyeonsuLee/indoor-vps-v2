package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.polygon.GenerateEdgeWidthPolygonsUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * map_edge.width_m 기반으로 floor_area_polygon 을 일괄 생성하는 admin endpoint.
 * 사용자가 웹 매니저에서 엣지에 폭을 입력해두면 이 endpoint 호출로 corridor-strip
 * 형태 폴리곤이 만들어진다. 재실행 안전 (edge_width:% 폴리곤만 갈아끼움).
 */
@RestController
@RequestMapping("/api/admin/areas")
@Tag(name = "Admin")
public class EdgePolygonAdminController {

    private final GenerateEdgeWidthPolygonsUseCase useCase;

    public EdgePolygonAdminController(GenerateEdgeWidthPolygonsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/{areaId}/polygons/from-edge-widths")
    public Map<String, Object> generate(@PathVariable UUID areaId) {
        GenerateEdgeWidthPolygonsUseCase.Result r = useCase.generate(areaId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("areaId", areaId.toString());
        body.put("wipedExisting", r.wiped());
        body.put("created", r.created());
        body.put("skippedNoWidth", r.skippedNoWidth());
        body.put("skippedDegenerate", r.skippedDegenerate());
        body.put("totalEdges", r.totalEdges());
        return body;
    }
}
