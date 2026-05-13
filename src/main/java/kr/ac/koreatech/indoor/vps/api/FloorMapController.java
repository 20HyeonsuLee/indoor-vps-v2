package kr.ac.koreatech.indoor.vps.api;

import static kr.ac.koreatech.indoor.vps.api.dto.MapDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.application.navigation.NavigationApplicationService;
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
    private final NavigationApplicationService service;

    public FloorMapController(NavigationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/floors/{floorId}/path")
    public FloorPathResponse getFloorPath(@PathVariable UUID floorId) {
        return service.getFloorPath(floorId);
    }

    @GetMapping("/floors/{floorId}/map")
    public ResponseEntity<FloorMapResponse> getFloorMap(
            @PathVariable UUID floorId,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    ) {
        FloorMapResponse response = service.getFloorMap(floorId);
        String etag = "\"" + response.etag() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(response);
    }
}
