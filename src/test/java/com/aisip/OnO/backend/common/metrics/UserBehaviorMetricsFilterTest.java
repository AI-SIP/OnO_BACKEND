package com.aisip.OnO.backend.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserBehaviorMetricsFilterTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final UserBehaviorMetricsFilter filter = new UserBehaviorMetricsFilter(meterRegistry);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        meterRegistry.close();
    }

    @Test
    void recordsApiBehaviorMetricWithStableLabels() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/problems/v2");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/problems/v2");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod("createProblem"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Timer timer = meterRegistry.find(UserBehaviorMetricsFilter.METRIC_NAME)
                .tags(
                        "domain", "problems",
                        "action", "createProblem",
                        "action_type", "create",
                        "outcome", "success",
                        "authenticated", "true",
                        "authority", "member",
                        "method", "POST",
                        "uri", "/api/problems/v2"
                )
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void skipsNonApiRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(meterRegistry.find(UserBehaviorMetricsFilter.METRIC_NAME).timer()).isNull();
    }

    @Test
    void recordsSecurityRejectedApiRequestBeforeControllerMapping() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(401);

        filter.doFilter(request, response, filterChain);

        Timer timer = meterRegistry.find(UserBehaviorMetricsFilter.METRIC_NAME)
                .tags(
                        "domain", "problems",
                        "action", "security_rejected",
                        "action_type", "read",
                        "outcome", "client_error",
                        "authenticated", "false",
                        "authority", "anonymous",
                        "method", "GET",
                        "uri", "/api/problems/{id}"
                )
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordsExceptionAsServerErrorWhenStatusWasNotSet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems/1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/problems/{problemId}");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod("getProblem"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            throw new RuntimeException("failed");
        };

        try {
            filter.doFilter(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        Timer timer = meterRegistry.find(UserBehaviorMetricsFilter.METRIC_NAME)
                .tags("outcome", "server_error", "uri", "/api/problems/{problemId}")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    private static class TestController {
        void createProblem() {
        }

        void getProblem() {
        }
    }
}
