package kr.ac.koreatech.indoor.vps.contexts.mapping.infrastructure.web.dto;

public final class AreaDtos {
    private AreaDtos() {
    }

    public record CreateAreaRequest(String label) {
    }
}
