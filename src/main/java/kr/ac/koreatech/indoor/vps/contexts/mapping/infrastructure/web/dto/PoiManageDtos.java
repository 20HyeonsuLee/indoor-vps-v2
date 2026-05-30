package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * POI 변이용 DTO. 기존 PoiDtos(read)는 별도 파일로 유지.
 */
public final class PoiManageDtos {
    private PoiManageDtos() {
    }

    public record PoiCreateRequest(
            @NotNull UUID areaId,
            @NotBlank String name,
            @NotBlank String category,
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double z,
            Double displayX,
            Double displayY,
            Double displayZ,
            UUID routeNodeId
    ) {
    }

    public record PoiUpdateRequest(
            String name,
            String category,
            String label,
            Double x,
            Double y,
            Double z,
            Double displayX,
            Double displayY,
            Double displayZ,
            UUID routeNodeId,
            Boolean detachRouteNode,
            Boolean markReviewed
    ) {
    }

    public record PoiAttachRequest(@NotNull UUID routeNodeId) {
    }
}
