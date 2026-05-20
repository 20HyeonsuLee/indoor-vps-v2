package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.PointcloudFileResolver;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.scan.PointcloudFileResolver.PointcloudResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "포인트클라우드")
public class PointcloudController {

    private final PointcloudFileResolver resolver;

    public PointcloudController(PointcloudFileResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/floors/{floorId}/pointcloud")
    public ResponseEntity<Resource> floorPointcloud(@PathVariable UUID floorId) {
        return respond(resolver.resolveForFloor(floorId), "floor-" + floorId + ".ply");
    }

    /** B(rtabmap-native) variant 시각화용. A 응답에는 영향 없음. */
    @GetMapping("/floors/{floorId}/pointcloud/v2")
    public ResponseEntity<Resource> floorPointcloudV2(@PathVariable UUID floorId) {
        return respond(resolver.resolveForFloorV2(floorId), "floor-" + floorId + "-v2.ply");
    }

    @GetMapping("/areas/{areaId}/pointcloud")
    public ResponseEntity<Resource> areaPointcloud(@PathVariable UUID areaId) {
        return respond(resolver.resolveForArea(areaId), "area-" + areaId + ".ply");
    }

    private ResponseEntity<Resource> respond(PointcloudResource ply, String downloadName) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(ply.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(ply.resource());
    }
}
