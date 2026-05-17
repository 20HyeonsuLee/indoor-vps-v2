package kr.ac.koreatech.indoor.vps.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        ContentCachingRequestWrapper wrappedRequest = wrapRequest(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        HttpServletRequest effectiveRequest = wrappedRequest != null ? wrappedRequest : request;

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(effectiveRequest, wrappedResponse);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            try {
                logExchange(effectiveRequest, wrappedRequest, wrappedResponse, elapsedMs);
            } finally {
                wrappedResponse.copyBodyToResponse();
                MDC.remove(MDC_KEY);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (header == null || header.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return header;
    }

    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (!HttpLogBodyCapture.shouldCaptureBody(request.getContentType())) {
            return null;
        }
        return new ContentCachingRequestWrapper(request, HttpLogBodyCapture.BODY_CAP_BYTES);
    }

    private void logExchange(
            HttpServletRequest request,
            ContentCachingRequestWrapper wrappedRequest,
            ContentCachingResponseWrapper wrappedResponse,
            long elapsedMs
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("requestId", MDC.get(MDC_KEY));
        entry.put("method", request.getMethod());
        entry.put("uri", fullUri(request));
        entry.put("status", wrappedResponse.getStatus());
        entry.put("durationMs", elapsedMs);
        entry.put("remote", request.getRemoteAddr());
        entry.put("contentLength", request.getContentLengthLong());
        entry.put("responseSize", wrappedResponse.getContentSize());
        entry.put("requestHeaders", requestHeadersOf(request));
        entry.put("responseHeaders", responseHeadersOf(wrappedResponse));
        entry.put("requestBody", HttpLogBodyCapture.request(wrappedRequest));
        entry.put("responseBody", HttpLogBodyCapture.response(wrappedResponse));
        log.info("http_request {}", entry);
    }

    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + query;
    }

    private Map<String, String> requestHeadersOf(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name, maskedValue(name, request.getHeader(name))));
        return headers;
    }

    private Map<String, String> responseHeadersOf(ContentCachingResponseWrapper response) {
        return response.getHeaderNames().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> maskedValue(name, joinHeader(response.getHeaders(name))),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private String joinHeader(java.util.Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private String maskedValue(String name, String value) {
        if (value == null) {
            return "-";
        }
        if (SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return "***";
        }
        return value.replaceAll("\\s+", " ");
    }
}
