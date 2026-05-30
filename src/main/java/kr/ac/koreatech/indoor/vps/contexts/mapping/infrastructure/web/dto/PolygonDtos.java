package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PolygonDtos {
    private PolygonDtos() {
    }

    public record VertexPayload(double x, double y, double z) {
    }

    public record PolygonRequest(
            @NotNull @Size(min = 3) List<VertexPayload> exterior
    ) {
    }
}
