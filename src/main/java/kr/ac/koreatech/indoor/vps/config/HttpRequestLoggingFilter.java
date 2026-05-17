package kr.ac.koreatech.indoor.vps.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "http_request method={} uri={} status={} durationMs={} remote={} contentLength={} userAgent={}",
                    request.getMethod(),
                    fullUri(request),
                    response.getStatus(),
                    elapsedMs,
                    request.getRemoteAddr(),
                    request.getContentLengthLong(),
                    userAgent(request)
            );
        }
    }

    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + query;
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "-";
        }
        return userAgent.replaceAll("\\s+", " ");
    }
}
