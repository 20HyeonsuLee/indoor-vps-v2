package kr.ac.koreatech.indoor.vps.api;

import static kr.ac.koreatech.indoor.vps.api.dto.SlamDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.ac.koreatech.indoor.vps.application.SlamLocalizationService;
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

    public SlamController(SlamLocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @PostMapping("/v3/localize")
    public SLAMLocalizeResponse localizeUploadedImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(name = "building_id", required = false) String buildingId,
            @RequestParam(name = "map_id", required = false) String mapId
    ) {
        return localizationService.localize(images, buildingId, mapId);
    }
}
