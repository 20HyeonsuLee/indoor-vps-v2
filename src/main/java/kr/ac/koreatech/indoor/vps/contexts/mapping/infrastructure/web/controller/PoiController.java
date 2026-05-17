package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.PoiDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.poi.PoiUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "POI")
public class PoiController {
    private final PoiUseCase service;

    public PoiController(PoiUseCase service) {
        this.service = service;
    }

    @GetMapping("/buildings/{buildingId}/pois")
    public List<POIResponse> listPois(@PathVariable UUID buildingId) {
        return service.listPois(buildingId);
    }

    @GetMapping("/buildings/{buildingId}/pois/search")
    public List<POIResponse> searchPois(
            @PathVariable UUID buildingId,
            @RequestParam(name = "query", required = false) String query
    ) {
        return service.searchPois(buildingId, query);
    }
}
