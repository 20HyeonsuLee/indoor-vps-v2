package kr.ac.koreatech.indoor.vps.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

final class HttpLogBodyCapture {
    static final int BODY_CAP_BYTES = 4096;
    private static final Set<String> LOGGABLE_BODY_TYPES = Set.of(
            "application/json", "application/x-www-form-urlencoded");

    private HttpLogBodyCapture() {}

    static String request(ContentCachingRequestWrapper wrapped) {
        if (wrapped == null) {
            return "<not-captured>";
        }
        return decode(wrapped.getContentAsByteArray(), charsetOf(wrapped.getContentType()));
    }

    static String response(ContentCachingResponseWrapper wrapped) {
        if (!shouldCaptureBody(wrapped.getContentType())) {
            return "<not-captured>";
        }
        return decode(wrapped.getContentAsByteArray(), charsetOf(wrapped.getContentType()));
    }

    static boolean shouldCaptureBody(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String baseType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (LOGGABLE_BODY_TYPES.contains(baseType)) {
            return true;
        }
        if (baseType.startsWith("text/")) {
            return true;
        }
        return baseType.endsWith("+json") || baseType.endsWith("+xml");
    }

    private static String decode(byte[] bytes, Charset charset) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int limit = Math.min(bytes.length, BODY_CAP_BYTES);
        String body = new String(bytes, 0, limit, charset);
        if (bytes.length > BODY_CAP_BYTES) {
            return body + "...<truncated " + (bytes.length - BODY_CAP_BYTES) + " bytes>";
        }
        return body;
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        for (String part : contentType.split(";")) {
            String token = part.trim();
            if (token.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                return resolve(token.substring("charset=".length()));
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset resolve(String name) {
        if (name == null || name.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
