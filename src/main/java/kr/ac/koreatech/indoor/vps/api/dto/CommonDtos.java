package kr.ac.koreatech.indoor.vps.api.dto;

import java.util.Map;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record ClientApiErrorResponse(String code, String message, Map<String, Object> detail) {
    }
}
