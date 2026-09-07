package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * JWT 인증 필터.
 *
 * <p>이 필터는 요청마다 "이 요청의 주체가 누구인가"를 정하고, 정할 수 없으면 그 이유를
 * {@link JwtTokenFilter#AUTH_ERROR_CASE_ATTRIBUTE} 로 넘겨 {@link CustomAuthenticationEntryPoint}
 * 가 401 바디를 만들게 한다. 여기서 실패 사유를 뭉개면 프론트가 "토큰 만료 → 갱신" 흐름을
 * 타지 못하고 그대로 로그아웃된다. 그래서 사유별 분기를 각각 고정한다.
 *
 * <p>필터는 인증 실패 자체를 예외로 던지지 않고 항상 체인을 이어간다. 최종 거절은 시큐리티가 한다.
 */
@DisplayName("JWT 인증 필터")
class JwtTokenFilterTest {

    private static final String VALID_TOKEN = "valid.access.token";

    private JwtTokenizer jwtTokenizer;
    private RedisTokenService redisTokenService;
    private JwtTokenFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
        jwtTokenizer = mock(JwtTokenizer.class);
        redisTokenService = mock(RedisTokenService.class);
        filter = new JwtTokenFilter(jwtTokenizer, redisTokenService);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWith(String authorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems");
        if (authorizationHeader != null) {
            request.addHeader(JwtTokenFilter.AUTHORIZATION_HEADER, authorizationHeader);
        }
        return request;
    }

    private void givenValidToken(long userId, Authority authority) {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(String.valueOf(userId));
        given(claims.get("authority", String.class)).willReturn(authority.name());
        given(jwtTokenizer.getClaimsFromAccessToken(VALID_TOKEN)).willReturn(claims);
        given(redisTokenService.isBlacklisted(VALID_TOKEN)).willReturn(false);
    }

    private MockFilterChain doFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private Object errorAttribute(MockHttpServletRequest request) {
        return request.getAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE);
    }

    @Nested
    @DisplayName("필터를 타지 않는 경로")
    class SkippedPaths {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "/actuator/health",
                "/management/actuator/prometheus",
                "/grafana",
                "/grafana/d/abc",
                "/prometheus",
                "/prometheus/metrics",
                "/",
                "/robots.txt",
                "/home",
                "/images/logo.png",
                "/perform-login",
                "/login",
                "/css/main.css",
                "/js/app.js",
                "/swagger-ui/index.html",
                "/v3/api-docs/swagger-config"
        })
        @DisplayName("공개 경로는 토큰 파싱 없이 통과시킨다")
        void skipsPublicPaths(String path) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            assertThat(filter.shouldNotFilter(request))
                    .as("공개 경로 %s 에 토큰 검증을 걸면 헬스체크/문서가 401 이 된다", path)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "/api/problems",
                "/api/auth/refresh",
                "/admin/main",
                "/loginx",
                "/homepage",
                "/image/1.png",
                "/swagger-ui",
                "/v3/api-docs"
        })
        @DisplayName("보호 경로는 필터를 탄다 - 접두사만 비슷한 경로가 새어 나가면 안 된다")
        void filtersProtectedPaths(String path) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            assertThat(filter.shouldNotFilter(request))
                    .as("%s 는 인증 검사를 받아야 한다", path)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Authorization 헤더 해석")
    class HeaderParsing {

        @Test
        @DisplayName("헤더가 없으면 토큰 검증 없이 익명으로 통과한다")
        void passesThroughWithoutHeader() throws Exception {
            MockHttpServletRequest request = requestWith(null);

            MockFilterChain chain = doFilter(request);

            assertThat(chain.getRequest()).as("체인은 항상 이어져야 한다").isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(errorAttribute(request))
                    .as("헤더 자체가 없는 건 '실패'가 아니라 '익명'이다")
                    .isNull();
            verifyNoInteractions(jwtTokenizer, redisTokenService);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"Basic abcdef", "bearer lower-case", "Bearer", "abc.def.ghi"})
        @DisplayName("Bearer 형식이 아니면 토큰으로 보지 않는다")
        void ignoresNonBearerHeader(String header) throws Exception {
            MockHttpServletRequest request = requestWith(header);

            doFilter(request);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(jwtTokenizer, redisTokenService);
        }

        @Test
        @DisplayName("Bearer 뒤 공백은 잘라내고 검증한다")
        void trimsTokenValue() throws Exception {
            givenValidToken(42L, Authority.ROLE_MEMBER);

            doFilter(requestWith("Bearer   " + VALID_TOKEN + "  "));

            verify(jwtTokenizer).validateAccessToken(VALID_TOKEN);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("유효한 토큰")
    class ValidToken {

        @Test
        @DisplayName("userId 를 principal 로, authority 를 권한으로 세운다")
        void setsAuthentication() throws Exception {
            givenValidToken(77L, Authority.ROLE_ADMIN);

            doFilter(requestWith("Bearer " + VALID_TOKEN));

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal())
                    .as("모든 소유권 검증이 이 principal 을 userId 로 신뢰한다")
                    .isEqualTo(77L);
            assertThat(authentication.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("MDC 에 userId 와 authority 를 남긴다")
        void putsUserIdAndAuthorityIntoMdc() throws Exception {
            givenValidToken(77L, Authority.ROLE_GUEST);

            doFilter(requestWith("Bearer " + VALID_TOKEN));

            assertThat(MDC.get("userId")).isEqualTo("77");
            assertThat(MDC.get("authority")).isEqualTo("ROLE_GUEST");
        }

        @Test
        @DisplayName("만료 검증을 블랙리스트 조회보다 먼저 한다")
        void validatesBeforeBlacklistLookup() throws Exception {
            givenValidToken(1L, Authority.ROLE_MEMBER);

            doFilter(requestWith("Bearer " + VALID_TOKEN));

            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jwtTokenizer, redisTokenService);
            inOrder.verify(jwtTokenizer).validateAccessToken(VALID_TOKEN);
            inOrder.verify(redisTokenService).isBlacklisted(VALID_TOKEN);
        }

        @Test
        @DisplayName("인증에 성공하면 에러 사유를 남기지 않는다")
        void leavesNoErrorAttribute() throws Exception {
            givenValidToken(1L, Authority.ROLE_MEMBER);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request)).isNull();
        }
    }

    @Nested
    @DisplayName("거절 사유 구분")
    class RejectionReasons {

        @Test
        @DisplayName("로그아웃된(블랙리스트) 토큰은 INVALID_ACCESS_TOKEN 이다")
        void marksBlacklistedToken() throws Exception {
            given(redisTokenService.isBlacklisted(VALID_TOKEN)).willReturn(true);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request)).isEqualTo(AuthErrorCase.INVALID_ACCESS_TOKEN);
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .as("블랙리스트 토큰으로 인증되면 로그아웃이 무의미해진다")
                    .isNull();
            verify(jwtTokenizer, never()).getClaimsFromAccessToken(anyString());
        }

        @Test
        @DisplayName("블랙리스트로 걸러도 체인은 계속 이어진다")
        void continuesChainForBlacklistedToken() throws Exception {
            given(redisTokenService.isBlacklisted(VALID_TOKEN)).willReturn(true);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            MockFilterChain chain = doFilter(request);

            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("만료된 토큰은 ACCESS_TOKEN_EXPIRED 다 - 프론트의 갱신 트리거")
        void marksExpiredToken() throws Exception {
            // JwtTokenizer 는 만료를 ApplicationException(ACCESS_TOKEN_EXPIRED) 로 감싸 던진다.
            // 필터는 그 errorCase 를 그대로 전달한다.
            willThrow(new ApplicationException(AuthErrorCase.ACCESS_TOKEN_EXPIRED))
                    .given(jwtTokenizer).validateAccessToken(VALID_TOKEN);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request))
                    .as("만료를 일반 인증 실패로 뭉개면 앱이 토큰 갱신 대신 로그아웃한다")
                    .isEqualTo(AuthErrorCase.ACCESS_TOKEN_EXPIRED);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(redisTokenService);
        }

        @Test
        @DisplayName("서명이 깨진 토큰은 INVALID_ACCESS_TOKEN 이다")
        void marksMalformedToken() throws Exception {
            // 만료가 아닌 검증 실패는 INVALID_ACCESS_TOKEN 이다.
            // 만료(1005)와 갈라놔야 프론트가 갱신할 이유 없는 토큰에 갱신을 걸지 않는다.
            willThrow(new ApplicationException(AuthErrorCase.INVALID_ACCESS_TOKEN))
                    .given(jwtTokenizer).validateAccessToken(VALID_TOKEN);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request)).isEqualTo(AuthErrorCase.INVALID_ACCESS_TOKEN);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("예상 못 한 예외는 AUTHENTICATION_FAILED 로 떨어뜨린다")
        void marksUnexpectedFailure() throws Exception {
            willThrow(new JwtException("signature mismatch"))
                    .given(jwtTokenizer).validateAccessToken(VALID_TOKEN);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request))
                    .as("ErrorCase 를 못 정하는 예외까지 500 으로 새면 안 된다")
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("subject 가 숫자가 아니면 500 이 아니라 인증 실패로 처리한다")
        void marksNonNumericSubject() throws Exception {
            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn("not-a-number");
            given(jwtTokenizer.getClaimsFromAccessToken(VALID_TOKEN)).willReturn(claims);
            given(redisTokenService.isBlacklisted(VALID_TOKEN)).willReturn(false);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request)).isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("모르는 권한 값이면 인증 실패로 처리한다")
        void marksUnknownAuthority() throws Exception {
            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn("1");
            given(claims.get("authority", String.class)).willReturn("ROLE_SUPERUSER");
            given(jwtTokenizer.getClaimsFromAccessToken(VALID_TOKEN)).willReturn(claims);
            given(redisTokenService.isBlacklisted(VALID_TOKEN)).willReturn(false);
            MockHttpServletRequest request = requestWith("Bearer " + VALID_TOKEN);

            doFilter(request);

            assertThat(errorAttribute(request))
                    .as("토큰에 없는 권한이 들어와도 승격되거나 500 이 나면 안 된다")
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("어떤 실패에서도 예외를 던지지 않고 체인을 이어간다")
        void neverThrows() throws Exception {
            willThrow(new JwtException("broken"))
                    .given(jwtTokenizer).validateAccessToken(VALID_TOKEN);

            MockFilterChain chain = doFilter(requestWith("Bearer " + VALID_TOKEN));

            assertThat(chain.getRequest())
                    .as("필터가 예외를 던지면 401 바디 대신 500 이 나간다")
                    .isNotNull();
        }
    }
}
