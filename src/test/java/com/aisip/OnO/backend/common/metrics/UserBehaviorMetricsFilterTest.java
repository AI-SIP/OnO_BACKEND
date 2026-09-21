package com.aisip.OnO.backend.common.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 행동 지표 필터.
 *
 * <p>이 필터가 붙이는 태그는 그대로 Prometheus 라벨이 된다. 라벨이 요청마다 달라지면
 * (예: URI 에 문제 id 가 그대로 들어가면) 시계열이 폭발해 대시보드가 죽는다.
 * 그래서 "동적 값이 반드시 {id} 로 접히는가", "매핑되지 않은 요청도 유한한 라벨로 떨어지는가"를 고정한다.
 *
 * <p>인증 태그는 인증 객체가 없을 때 MDC 폴백을 쓴다. 이 필터는 시큐리티 필터보다 먼저 도는
 * 구간이 있어서, 폴백이 없으면 로그인 사용자의 요청이 전부 anonymous 로 집계된다.
 */
@DisplayName("사용자 행동 지표 필터")
class UserBehaviorMetricsFilterTest {

    private MeterRegistry meterRegistry;
    private UserBehaviorMetricsFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
        meterRegistry = new SimpleMeterRegistry();
        filter = new UserBehaviorMetricsFilter(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
        meterRegistry.close();
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private void dispatch(MockHttpServletRequest request) throws Exception {
        dispatch(request, 200);
    }

    private void dispatch(MockHttpServletRequest request, int status) throws Exception {
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(status);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    private void dispatchWithFailure(MockHttpServletRequest request, int statusBeforeFailure) {
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(statusBeforeFailure);
            throw new IllegalStateException("handler blew up");
        };
        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception expected) {
            // 필터는 예외를 삼키지 않는다. 계측만 하고 그대로 올려보낸다.
        }
    }

    /** 이 테스트의 요청 하나가 만든 유일한 타이머의 태그를 읽는다. */
    private String tag(String key) {
        Meter meter = meterRegistry.find(UserBehaviorMetricsFilter.METRIC_NAME).meter();
        assertThat(meter).as("요청이 계측되지 않았다").isNotNull();
        return meter.getId().getTag(key);
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    @Nested
    @DisplayName("계측 대상")
    class Scope {

        @Test
        @DisplayName("API 요청은 안정적인 라벨 조합으로 계측된다")
        void recordsApiRequestWithStableLabels() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            1L, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

            MockHttpServletRequest request = request("POST", "/api/problems/v2");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/problems/v2");
            request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod("createProblem"));

            dispatch(request);

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

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/home", "/actuator/health", "/login", "/api", "/apix/problems"})
        @DisplayName("API 가 아닌 요청은 계측하지 않는다 - 정적 리소스로 시계열을 늘리지 않는다")
        void skipsNonApiRequests(String uri) {
            assertThat(filter.shouldNotFilter(request("GET", uri))).isTrue();
        }

        @Test
        @DisplayName("URI 를 알 수 없는 요청도 계측하지 않는다")
        void skipsRequestWithoutUri() {
            MockHttpServletRequest request = request("GET", "/api/problems");
            request.setRequestURI(null);

            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("API 요청은 계측한다")
        void filtersApiRequests() {
            assertThat(filter.shouldNotFilter(request("GET", "/api/problems"))).isFalse();
        }

        @Test
        @DisplayName("핸들러가 예외를 던져도 계측한 뒤 예외를 그대로 올린다")
        void recordsEvenWhenHandlerThrows() {
            MockHttpServletRequest request = request("GET", "/api/problems/1");

            dispatchWithFailure(request, 200);

            assertThat(tag("outcome"))
                    .as("상태코드가 세팅되기 전에 터진 요청은 서버 오류로 집계해야 한다")
                    .isEqualTo("server_error");
        }

        @Test
        @DisplayName("이미 4xx 로 응답이 정해진 뒤 터진 예외는 그 상태를 유지한다")
        void keepsClientErrorStatusWhenFailureFollows() {
            MockHttpServletRequest request = request("GET", "/api/problems/1");

            dispatchWithFailure(request, 400);

            assertThat(tag("outcome"))
                    .as("클라이언트 오류를 서버 오류로 올려 세면 에러율 경보가 오작동한다")
                    .isEqualTo("client_error");
        }
    }

    @Nested
    @DisplayName("action_type - HTTP 메서드 분류")
    class ActionType {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "GET,read",
                "POST,create",
                "PUT,update",
                "PATCH,update",
                "DELETE,delete",
                "OPTIONS,other",
                "HEAD,other"
        })
        @DisplayName("메서드를 읽기/생성/수정/삭제로 접는다")
        void classifiesHttpMethod(String method, String expected) throws Exception {
            dispatch(request(method, "/api/problems"));

            assertThat(tag("action_type")).isEqualTo(expected);
        }

        @Test
        @DisplayName("소문자 메서드도 같은 분류로 접는다")
        void isCaseInsensitive() throws Exception {
            dispatch(request("get", "/api/problems"));

            assertThat(tag("action_type")).isEqualTo("read");
        }
    }

    @Nested
    @DisplayName("outcome - 상태코드 분류")
    class Outcome {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "200,success",
                "204,success",
                "301,redirect",
                "302,redirect",
                "400,client_error",
                "404,client_error",
                "429,client_error",
                "500,server_error",
                "503,server_error"
        })
        @DisplayName("상태코드를 성공/리다이렉트/클라이언트오류/서버오류로 접는다")
        void classifiesStatus(int status, String expected) throws Exception {
            dispatch(request("GET", "/api/problems"), status);

            assertThat(tag("outcome")).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("action - 무엇을 하려던 요청인가")
    class Action {

        @Test
        @DisplayName("컨트롤러가 매핑됐으면 메서드 이름을 쓴다")
        void usesHandlerMethodName() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/problems/1");
            request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod("getProblem"));

            dispatch(request);

            assertThat(tag("action")).isEqualTo("getProblem");
        }

        @ParameterizedTest(name = "status {0}")
        @ValueSource(ints = {401, 403})
        @DisplayName("시큐리티가 먼저 끊은 요청은 security_rejected 로 묶는다")
        void marksSecurityRejection(int status) throws Exception {
            dispatch(request("GET", "/api/problems/1"), status);

            assertThat(tag("action"))
                    .as("컨트롤러 매핑 전에 끊긴 요청도 유한한 라벨로 떨어져야 한다")
                    .isEqualTo("security_rejected");
        }

        @Test
        @DisplayName("매핑되지 않은 404 는 not_found 로 묶는다")
        void marksNotFound() throws Exception {
            dispatch(request("GET", "/api/unknown-endpoint"), 404);

            assertThat(tag("action")).isEqualTo("not_found");
        }

        @Test
        @DisplayName("그 밖의 매핑 실패는 unmapped_request 로 묶는다")
        void marksUnmappedRequest() throws Exception {
            dispatch(request("GET", "/api/problems"), 400);

            assertThat(tag("action")).isEqualTo("unmapped_request");
        }
    }

    @Nested
    @DisplayName("uri - 동적 경로 정규화")
    class UriPattern {

        @Test
        @DisplayName("핸들러 매핑 패턴이 있으면 그대로 쓴다")
        void prefersHandlerPattern() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/problems/12345");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/problems/{problemId}");

            dispatch(request);

            assertThat(tag("uri")).isEqualTo("/api/problems/{problemId}");
        }

        @Test
        @DisplayName("패턴이 빈 문자열이면 URI 정규화로 폴백한다")
        void fallsBackWhenPatternIsBlank() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/problems/12345");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "   ");

            dispatch(request);

            assertThat(tag("uri")).isEqualTo("/api/problems/{id}");
        }

        @Test
        @DisplayName("패턴 속성이 문자열이 아니어도 폴백한다")
        void fallsBackWhenPatternIsNotString() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/problems/12345");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, 42);

            dispatch(request);

            assertThat(tag("uri")).isEqualTo("/api/problems/{id}");
        }

        @Test
        @DisplayName("숫자 id 세그먼트는 {id} 로 접는다 - 시계열 폭발 방지")
        void collapsesNumericId() throws Exception {
            dispatch(request("GET", "/api/problems/98765/solves/12"));

            assertThat(tag("uri")).isEqualTo("/api/problems/{id}/solves/{id}");
        }

        @Test
        @DisplayName("UUID 세그먼트도 {id} 로 접는다")
        void collapsesUuid() throws Exception {
            dispatch(request("GET", "/api/study-rooms/550e8400-e29b-41d4-a716-446655440000/feed"));

            assertThat(tag("uri")).isEqualTo("/api/study-rooms/{id}/feed");
        }

        @Test
        @DisplayName("id 처럼 보이지 않는 세그먼트는 그대로 둔다")
        void keepsStaticSegments() throws Exception {
            dispatch(request("GET", "/api/problems/summary"));

            assertThat(tag("uri")).isEqualTo("/api/problems/summary");
        }
    }

    @Nested
    @DisplayName("domain - 어느 기능 영역인가")
    class Domain {

        @Test
        @DisplayName("경로의 첫 세그먼트를 도메인으로 쓴다")
        void usesFirstSegment() throws Exception {
            dispatch(request("GET", "/api/problems/1"));

            assertThat(tag("domain")).isEqualTo("problems");
        }

        @Test
        @DisplayName("하위 경로가 없어도 도메인을 뽑는다")
        void handlesSingleSegmentPath() throws Exception {
            dispatch(request("GET", "/api/problems"));

            assertThat(tag("domain")).isEqualTo("problems");
        }

        @Test
        @DisplayName("camelCase 도메인은 snake_case 로 정규화한다")
        void normalizesCamelCase() throws Exception {
            dispatch(request("POST", "/api/fileUpload/image"));

            assertThat(tag("domain")).isEqualTo("file_upload");
        }

        @Test
        @DisplayName("영숫자가 없는 도메인은 unknown 으로 접는다")
        void normalizesGarbageSegmentToUnknown() throws Exception {
            dispatch(request("GET", "/api/---/x"));

            assertThat(tag("domain"))
                    .as("스캐너가 만든 쓰레기 경로가 라벨이 되면 안 된다")
                    .isEqualTo("unknown");
        }

        @Test
        @DisplayName("/api/ 로 시작하지 않는 패턴이 잡히면 unknown 이다")
        void marksNonApiPatternAsUnknown() throws Exception {
            MockHttpServletRequest request = request("GET", "/api/problems/1");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");

            dispatch(request, 500);

            assertThat(tag("domain")).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("authenticated / authority - 누가 보낸 요청인가")
    class Identity {

        @Test
        @DisplayName("인증된 요청은 ROLE_ 접두사를 뗀 소문자 권한으로 집계한다")
        void usesAuthenticationAuthority() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            7L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authenticated")).isEqualTo("true");
            assertThat(tag("authority")).isEqualTo("admin");
        }

        @Test
        @DisplayName("인증 정보가 전혀 없으면 익명으로 집계한다")
        void marksAnonymousWithoutAuthentication() throws Exception {
            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authenticated")).isEqualTo("false");
            assertThat(tag("authority")).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("AnonymousAuthenticationToken 도 익명으로 본다")
        void treatsAnonymousTokenAsAnonymous() throws Exception {
            Authentication anonymous = new AnonymousAuthenticationToken(
                    "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            SecurityContextHolder.getContext().setAuthentication(anonymous);

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authenticated"))
                    .as("익명 토큰을 로그인으로 세면 로그인 전환율 지표가 틀어진다")
                    .isEqualTo("false");
            assertThat(tag("authority")).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("인증 객체가 아직 없어도 MDC 에 userId 가 있으면 로그인 요청으로 본다")
        void fallsBackToMdcUserId() throws Exception {
            MDC.put("userId", "7");

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authenticated"))
                    .as("이 필터는 시큐리티보다 먼저 돌 수 있어 MDC 폴백이 필요하다")
                    .isEqualTo("true");
        }

        @Test
        @DisplayName("MDC 의 authority 도 권한 태그로 쓴다")
        void fallsBackToMdcAuthority() throws Exception {
            MDC.put("userId", "7");
            MDC.put("authority", "ROLE_GUEST");

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authority")).isEqualTo("guest");
        }

        @Test
        @DisplayName("MDC authority 가 공백이면 익명으로 되돌린다")
        void ignoresBlankMdcAuthority() throws Exception {
            MDC.put("authority", "   ");

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authority")).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("아직 인증되지 않은 토큰이 올라와 있어도 익명으로 집계한다")
        void treatsUnauthenticatedTokenAsAnonymous() throws Exception {
            Authentication notYetAuthenticated =
                    new UsernamePasswordAuthenticationToken("principal", "credentials");
            SecurityContextHolder.getContext().setAuthentication(notYetAuthenticated);

            dispatch(request("GET", "/api/problems"));

            assertThat(notYetAuthenticated.isAuthenticated())
                    .as("인증 전 토큰이라는 전제를 고정한다")
                    .isFalse();
            assertThat(tag("authenticated")).isEqualTo("false");
            assertThat(tag("authority")).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("권한이 하나도 없는 인증은 unknown 으로 집계한다")
        void marksAuthenticationWithoutAuthorities() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(7L, null, List.of()));

            dispatch(request("GET", "/api/problems"));

            assertThat(tag("authenticated")).isEqualTo("true");
            assertThat(tag("authority")).isEqualTo("unknown");
        }
    }

    private static class TestController {
        void createProblem() {
        }

        void getProblem() {
        }
    }
}
