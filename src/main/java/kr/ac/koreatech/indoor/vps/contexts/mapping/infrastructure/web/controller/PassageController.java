package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.controller;

import static kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto.PassageDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kr.ac.koreatech.indoor.vps.contexts.mapping.application.passage.PassageApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "수직 이동")
public class PassageController {
    private final PassageApplicationService service;

    public PassageController(PassageApplicationService service) {
        this.service = service;
    }

    @GetMapping("/buildings/{buildingId}/passages")
    public List<VerticalPassageResponse> listPassages(@PathVariable UUID buildingId) {
        return service.listPassages(buildingId);
    }
}
