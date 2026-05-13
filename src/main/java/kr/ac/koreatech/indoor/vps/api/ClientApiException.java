package kr.ac.koreatech.indoor.vps.api;

import java.util.Map;
import org.springframework.http.HttpStatusCode;

public final class ClientApiException extends RuntimeException {
    private final HttpStatusCode statusCode;
    private final String code;
    private final Map<String, Object> detail;

    public ClientApiException(HttpStatusCode statusCode, String code, String message) {
        this(statusCode, code, message, null);
    }

    public ClientApiException(
            HttpStatusCode statusCode,
            String code,
            String message,
            Map<String, Object> detail
    ) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.detail = detail;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> detail() {
        return detail;
    }
}
