package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.SlamDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.SlamLocalizationService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.LocalizeCommand;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.LocalizeCommand.ImagePayload;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam.SLAMLocalizeResult;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService;
import kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.capture.FixtureCaptureService.CaptureRecord;
import kr.ac.koreatech.indoor.vps.shared.exception.ClientApiException;
import org.springframework.http.HttpStatus;
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
            @RequestParam(name = "depths", required = false) Optional<List<MultipartFile>> depths,
            @RequestParam(name = "building_id", required = false) String buildingId,
            @RequestParam(name = "map_id", required = false) String mapId,
            @RequestParam(name = "floor_id", required = false) String floorId
    ) {
        CaptureRecord capture = fixtureCapture.captureLocalizeImages(images, buildingId, mapId);
        try {
            LocalizeCommand command = new LocalizeCommand(
                    toImagePayloads(images),
                    toDepthPayloads(depths.orElse(null)),
                    buildingId, mapId, floorId);
            SLAMLocalizeResult result = localizationService.localize(command);
            SLAMLocalizeResponse response = toResponse(result);
            fixtureCapture.writeResponse(capture, response);
            return response;
        } catch (RuntimeException e) {
            fixtureCapture.writeError(capture, e);
            throw e;
        }
    }

    private List<ImagePayload> toImagePayloads(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .map(f -> new ImagePayload(readBytes(f), f.getOriginalFilename(), f.getContentType()))
                .toList();
    }

    private List<LocalizeCommand.DepthPayload> toDepthPayloads(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        // Preserve order/length so depth at index i pairs with image at index i.
        // Empty entries become null so server falls back to 2D-3D PnP for that frame.
        java.util.List<LocalizeCommand.DepthPayload> out = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                out.add(null);
            } else {
                out.add(new LocalizeCommand.DepthPayload(readBytes(f), f.getOriginalFilename()));
            }
        }
        return out;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ClientApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", e.getMessage());
        }
    }

    private SLAMLocalizeResponse toResponse(SLAMLocalizeResult result) {
        return new SLAMLocalizeResponse(
                result.pose(),
                result.confidence(),
                result.numMatches(),
                result.matchedImageIndex(),
                result.floorId(),
                result.areaId(),
                result.floorLevel()
        );
    }
}
