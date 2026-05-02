package com.aisip.OnO.backend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingMdcFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String REAL_IP_HEADER = "X-Real-IP";

    @Value("${logging.http.slow-request-threshold-ms:1000}")
    private long slowRequestThresholdMs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/actuator/")
                || path.equals("/robots.txt")
                || path.startsWith("/images/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String traceId = resolveTraceId(request);

        MDC.put("traceId", traceId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        MDC.put("clientIp", resolveClientIp(request));
        response.setHeader(REQUEST_ID_HEADER, traceId);

        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            MDC.put("exceptionType", ex.getClass().getSimpleName());
            throw ex;
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            int status = failure == null ? response.getStatus() : resolveFailureStatus(response);

            MDC.put("status", String.valueOf(status));
            MDC.put("latencyMs", String.valueOf(latencyMs));
            MDC.put("uri", resolveUriPattern(request));

            logRequestCompleted(status, latencyMs);
            clearMdc();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private int resolveFailureStatus(HttpServletResponse response) {
        int status = response.getStatus();
        if (status < HttpServletResponse.SC_BAD_REQUEST) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return status;
    }

    private String resolveUriPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String matchedPattern && !matchedPattern.isBlank()) {
            return matchedPattern;
        }
        return request.getRequestURI();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }

        String realIp = request.getHeader(REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private void logRequestCompleted(int status, long latencyMs) {
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error("HTTP request completed");
            return;
        }

        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            log.warn("HTTP request completed");
            return;
        }

        if (latencyMs >= slowRequestThresholdMs) {
            log.info("HTTP slow request completed");
            return;
        }

        log.debug("HTTP request completed");
    }

    private void clearMdc() {
        MDC.remove("traceId");
        MDC.remove("userId");
        MDC.remove("method");
        MDC.remove("uri");
        MDC.remove("clientIp");
        MDC.remove("status");
        MDC.remove("latencyMs");
        MDC.remove("errorCode");
        MDC.remove("exceptionType");
    }
}
