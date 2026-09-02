package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code @RateLimit} 이 붙은 메서드 앞에서 사용자별 한도를 확인하는 어드바이스.
 */
@DisplayName("요청 한도 어드바이스")
class RateLimitAspectTest {

    private RateLimitService rateLimitService;
    private RateLimitAspect aspect;
    private RateLimit rateLimit;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        rateLimitService = mock(RateLimitService.class);
        aspect = new RateLimitAspect(rateLimitService);
        rateLimit = annotationOf("annotated");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("인증된 요청")
    class AuthenticatedRequest {

        @Test
        @DisplayName("한도 내면 통과시킨다")
        void passesWithinLimit() {
            authenticateAs(7L);
            when(rateLimitService.tryConsume(anyString(), anyLong(), anyInt())).thenReturn(true);

            assertThatCode(() -> aspect.checkRateLimit(rateLimit)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("어노테이션의 key/limit 와 인증된 userId 를 그대로 전달한다")
        void delegatesAnnotationValues() {
            authenticateAs(7L);
            when(rateLimitService.tryConsume(anyString(), anyLong(), anyInt())).thenReturn(true);

            aspect.checkRateLimit(rateLimit);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            verify(rateLimitService).tryConsume(key.capture(), userId.capture(), limit.capture());

            assertThat(key.getValue()).isEqualTo("presigned_url");
            assertThat(userId.getValue()).as("한도는 사용자별로 센다").isEqualTo(7L);
            assertThat(limit.getValue()).isEqualTo(200);
        }

        @Test
        @DisplayName("한도를 넘으면 429 ApplicationException 을 던진다")
        void throwsWhenLimitExceeded() {
            authenticateAs(7L);
            when(rateLimitService.tryConsume(anyString(), anyLong(), anyInt())).thenReturn(false);

            assertThatThrownBy(() -> aspect.checkRateLimit(rateLimit))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(ProblemErrorCase.ANALYSIS_RATE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("사용자마다 자신의 userId 로만 한도를 소비한다")
        void countsPerUser() {
            when(rateLimitService.tryConsume(anyString(), anyLong(), anyInt())).thenReturn(true);

            authenticateAs(1L);
            aspect.checkRateLimit(rateLimit);
            authenticateAs(2L);
            aspect.checkRateLimit(rateLimit);

            verify(rateLimitService).tryConsume("presigned_url", 1L, 200);
            verify(rateLimitService).tryConsume("presigned_url", 2L, 200);
        }
    }

    @Nested
    @DisplayName("인증되지 않은 요청")
    class UnauthenticatedRequest {

        @Test
        @DisplayName("인증 정보가 없으면 500 이 아니라 401 로 거절한다")
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> aspect.checkRateLimit(rateLimit))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED);

            verifyNoInteractions(rateLimitService);
        }

        @Test
        @DisplayName("익명 사용자(principal 이 Long 이 아님)도 401 로 거절한다")
        void rejectsAnonymousPrincipal() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

            assertThatThrownBy(() -> aspect.checkRateLimit(rateLimit))
                    .as("ClassCastException 으로 500 이 나가면 안 된다")
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED);

            verifyNoInteractions(rateLimitService);
        }
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private RateLimit annotationOf(String methodName) throws NoSuchMethodException {
        Method method = RateLimitedTarget.class.getDeclaredMethod(methodName);
        return method.getAnnotation(RateLimit.class);
    }

    static class RateLimitedTarget {

        @RateLimit(key = "presigned_url", limitPerDay = 200)
        void annotated() {
        }
    }
}
