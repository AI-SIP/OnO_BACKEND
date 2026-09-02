package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증/인가 실패 응답 계약.
 *
 * <p>{@link JwtTokenFilter} 가 남긴 실패 사유를 여기서 응답 바디로 옮긴다.
 * 앱은 errorCode 로 "토큰을 갱신할지, 로그아웃시킬지"를 판단하므로 사유별 코드가 뭉개지면
 * 사용자가 갱신 가능한 상황에서도 로그아웃된다.
 *
 * <p>동시에 공개 경로(헬스체크, 로그인 페이지, 스웨거)에서는 아무것도 쓰지 않아야 한다.
 * 여기서 401 JSON 을 써버리면 브라우저 화면 대신 JSON 이 뜨고, 헬스체크가 실패한다.
 */
@DisplayName("인증 실패 응답")
class AuthenticationFailureResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);
    private final CustomAccessDeniedHandler accessDeniedHandler = new CustomAccessDeniedHandler(objectMapper);

    private final AuthenticationException authenticationException = new BadCredentialsException("no credentials");

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    @Nested
    @DisplayName("공개 경로")
    class PublicPaths {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "/actuator/health",
                "/grafana",
                "/grafana/d/board",
                "/prometheus",
                "/prometheus/metrics",
                "/api/auth/login",
                "/",
                "/robots.txt",
                "/home",
                "/login",
                "/login?error",
                "/swagger-ui/index.html",
                "/v3/api-docs"
        })
        @DisplayName("401 바디를 쓰지 않고 그대로 둔다")
        void writesNothing(String uri) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request(uri), response, authenticationException);

            assertThat(response.getStatus())
                    .as("%s 에서 상태코드를 바꾸면 로그인 화면/헬스체크가 깨진다", uri)
                    .isEqualTo(200);
            assertThat(response.getContentAsString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("보호 경로")
    class ProtectedPaths {

        @Test
        @DisplayName("실패 사유가 없으면 401 + 인증 실패(1007)로 응답한다")
        void defaultsToAuthenticationFailed() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request("/api/problems"), response, authenticationException);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body(response).get("errorCode").asInt())
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode());
            assertThat(body(response).get("message").asText())
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED.getMessage());
        }

        @Test
        @DisplayName("만료 사유가 전달되면 1005 로 응답한다 - 앱의 토큰 갱신 신호")
        void keepsExpiredReason() throws Exception {
            MockHttpServletRequest request = request("/api/problems");
            request.setAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.ACCESS_TOKEN_EXPIRED);
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response, authenticationException);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body(response).get("errorCode").asInt())
                    .as("만료(1005)가 인증 실패(1007)로 뭉개지면 갱신 흐름이 끊긴다")
                    .isEqualTo(AuthErrorCase.ACCESS_TOKEN_EXPIRED.getErrorCode());
        }

        @Test
        @DisplayName("로그아웃된 토큰 사유가 전달되면 1009 로 응답한다")
        void keepsInvalidTokenReason() throws Exception {
            MockHttpServletRequest request = request("/api/problems");
            request.setAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.INVALID_ACCESS_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response, authenticationException);

            assertThat(body(response).get("errorCode").asInt())
                    .isEqualTo(AuthErrorCase.INVALID_ACCESS_TOKEN.getErrorCode());
        }

        @Test
        @DisplayName("ErrorCase 가 아닌 값이 들어 있으면 기본 인증 실패로 되돌린다")
        void ignoresNonErrorCaseAttribute() throws Exception {
            MockHttpServletRequest request = request("/api/problems");
            request.setAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE, "ACCESS_TOKEN_EXPIRED");
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response, authenticationException);

            assertThat(body(response).get("errorCode").asInt())
                    .as("문자열이 들어와도 ClassCastException 으로 500 이 되면 안 된다")
                    .isEqualTo(AuthErrorCase.AUTHENTICATION_FAILED.getErrorCode());
        }

        @Test
        @DisplayName("UTF-8 JSON 으로 응답한다")
        void writesUtf8Json() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request("/api/users/me"), response, authenticationException);

            assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
            assertThat(response.getContentAsString())
                    .as("한글 메시지가 깨지면 앱이 그대로 노출한다")
                    .contains(AuthErrorCase.AUTHENTICATION_FAILED.getMessage());
        }

        @Test
        @DisplayName("접두사만 비슷한 경로는 공개 경로로 보지 않는다")
        void doesNotTreatLookalikePathAsPublic() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request("/api/authorization-test"), response, authenticationException);

            assertThat(response.getStatus())
                    .as("/api/auth 로 시작하는 경로 규칙은 현재 접두사 매칭이다")
                    .isEqualTo(200);

            MockHttpServletResponse other = new MockHttpServletResponse();
            entryPoint.commence(request("/apis/auth"), other, authenticationException);
            assertThat(other.getStatus()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("권한 부족")
    class AccessDenied {

        @Test
        @DisplayName("403 + 접근 권한 없음(1008)으로 응답한다")
        void returnsForbidden() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            accessDeniedHandler.handle(request("/admin/main"), response,
                    new AccessDeniedException("denied"));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
            assertThat(body(response).get("errorCode").asInt())
                    .as("권한 부족(403)을 인증 실패(401)로 내리면 앱이 무한 재로그인에 빠진다")
                    .isEqualTo(AuthErrorCase.ACCESS_DENIED.getErrorCode());
        }
    }
}
