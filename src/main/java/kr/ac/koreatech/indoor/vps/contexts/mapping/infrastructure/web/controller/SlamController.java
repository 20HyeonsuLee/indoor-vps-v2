package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.SlamDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SLAMLocalizeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService.CaptureRecord;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/slam")
@Tag(name = "SLAM 위치추정")
public class SlamController {
    private final SlamLocalizationService localizationService;
    private final FixtureCaptureService fixtureCapture;

    public SlamController(SlamLocalizationService localizationService, FixtureCaptureService fixtureCapture) {
        this.localizationService = localizationService;
        this.fixtureCapture = fixtureCapture;
    }

    @PostMapping("/v3/localize")
    public SLAMLocalizeResponse localizeUploadedImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(name = "building_id", required = false) String buildingId,
            @RequestParam(name = "map_id", required = false) String mapId
    ) {
        CaptureRecord capture = fixtureCapture.captureLocalizeImages(images, buildingId, mapId);
        try {
            SLAMLocalizeResult result = localizationService.localize(images, buildingId, mapId);
            SLAMLocalizeResponse response = toResponse(result);
            fixtureCapture.writeResponse(capture, response);
            return response;
        } catch (RuntimeException e) {
            fixtureCapture.writeError(capture, e);
            throw e;
        }
    }

    private SLAMLocalizeResponse toResponse(SLAMLocalizeResult result) {
        return new SLAMLocalizeResponse(
                result.pose(),
                result.confidence(),
                result.mapId(),
                result.numMatches(),
                result.matchedImageIndex(),
                result.floorId(),
                result.floorLevel()
        );
    }
}
