package com.aisip.OnO.backend.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserBehaviorMetricsFilter extends OncePerRequestFilter {

    static final String METRIC_NAME = "ono.user.behavior.duration";
    private static final String API_PREFIX = "/api/";
    private static final String ID_PATTERN = "^\\d+$";
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final MeterRegistry meterRegistry;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Exception failure = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            record(request, response, failure, Math.max(0L, System.nanoTime() - startedAt));
        }
    }

    private void record(HttpServletRequest request,
                        HttpServletResponse response,
                        Exception failure,
                        long elapsedNanos) {
        int status = resolveStatus(response, failure);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Timer.builder(METRIC_NAME)
                .description("API user behavior duration grouped by endpoint action")
                .tags(
                        "domain", resolveDomain(request),
                        "action", resolveAction(request, status),
                        "action_type", resolveActionType(request.getMethod()),
                        "outcome", resolveOutcome(status),
                        "authenticated", String.valueOf(isAuthenticated(authentication)),
                        "authority", resolveAuthority(authentication),
                        "method", request.getMethod(),
                        "uri", resolveUriPattern(request)
                )
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private int resolveStatus(HttpServletResponse response, Exception failure) {
        int status = response.getStatus();
        if (failure != null && status < HttpServletResponse.SC_BAD_REQUEST) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return status;
    }

    private String resolveDomain(HttpServletRequest request) {
        String pattern = resolveUriPattern(request);
        if (!pattern.startsWith(API_PREFIX)) {
            return "unknown";
        }

        String remainder = pattern.substring(API_PREFIX.length());
        int nextSlash = remainder.indexOf('/');
        String domain = nextSlash >= 0 ? remainder.substring(0, nextSlash) : remainder;
        return normalizeLabelValue(domain);
    }

    private String resolveAction(HttpServletRequest request, int status) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getMethod().getName();
        }

        if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
            return "security_rejected";
        }
        if (status == HttpServletResponse.SC_NOT_FOUND) {
            return "not_found";
        }
        return "unmapped_request";
    }

    private String resolveActionType(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET" -> "read";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> "other";
        };
    }

    private String resolveOutcome(int status) {
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            return "server_error";
        }
        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            return "client_error";
        }
        if (status >= HttpServletResponse.SC_MULTIPLE_CHOICES) {
            return "redirect";
        }
        return "success";
    }

    private boolean isAuthenticated(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return true;
        }
        return MDC.get("userId") != null;
    }

    private String resolveAuthority(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .map(this::normalizeAuthority)
                    .orElse("unknown");
        }

        String authority = MDC.get("authority");
        if (authority != null && !authority.isBlank()) {
            return normalizeAuthority(authority);
        }
        return "anonymous";
    }

    private String resolveUriPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String matchedPattern && !matchedPattern.isBlank()) {
            return matchedPattern;
        }
        return normalizeDynamicPath(request.getRequestURI());
    }

    private String normalizeDynamicPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }

        String[] segments = uri.split("/");
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            normalized.append('/');
            if (segment.matches(ID_PATTERN) || segment.matches(UUID_PATTERN)) {
                normalized.append("{id}");
            } else {
                normalized.append(segment);
            }
        }
        return normalized.isEmpty() ? "/" : normalized.toString();
    }

    private String normalizeAuthority(String authority) {
        return authority.replaceFirst("^ROLE_", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeLabelValue(String value) {
        String normalized = value.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
