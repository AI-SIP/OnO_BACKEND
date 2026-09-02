package com.aisip.OnO.backend.admin.security;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 관리자 화면 전체에 대한 접근 통제 검증.
 *
 * <p>이 프로젝트에서 가장 위험한 지점이다. {@code /admin/**} 아래 한 엔드포인트만 뚫려도
 * 전체 사용자 목록·오답 문제 원문·복습노트·피드백이 그대로 노출되고,
 * {@code DELETE /admin/user/{id}} 는 남의 계정을 지울 수 있다.
 *
 * <p>따라서 "관리자 화면은 ROLE_ADMIN 만" 이라는 규칙을 컨트롤러 하나씩이 아니라
 * <b>엔드포인트 목록 전체</b>에 대해 확인한다. 새 관리자 엔드포인트가 추가됐는데
 * 이 목록에 들어오지 않으면 {@link #everyAdminMappingIsCoveredByThisTest()} 가 실패한다.
 */
@DisplayName("관리자 엔드포인트 접근 통제")
class AdminEndpointAuthorizationTest extends AdminTestSupport {

    private User member;

    @BeforeEach
    void setUpMember() {
        member = fixtures.createUser();
    }

    /**
     * 관리자 컨트롤러 7개의 모든 엔드포인트.
     *
     * <p>경로 변수에는 존재하지 않는 id를 넣는다. 권한 검사는 컨트롤러에 진입하기 전에
     * 끝나야 하므로, 데이터가 없어도 결과가 달라지면 안 된다.
     */
    static Stream<Object[]> adminEndpoints() {
        return Stream.of(
                // AdminController
                new Object[]{HttpMethod.GET, "/admin/main"},
                new Object[]{HttpMethod.GET, "/admin/user/image/view?url=https://example.com/a.png"},
                // AdminUserController
                new Object[]{HttpMethod.GET, "/admin/users"},
                new Object[]{HttpMethod.GET, "/admin/user/999999"},
                new Object[]{HttpMethod.POST, "/admin/user/999999"},
                new Object[]{HttpMethod.POST, "/admin/user/999999/level?levelType=attendance&levelValue=3&pointValue=5"},
                new Object[]{HttpMethod.DELETE, "/admin/user/999999"},
                // AdminProblemController
                new Object[]{HttpMethod.GET, "/admin/problems"},
                new Object[]{HttpMethod.GET, "/admin/problem/999999"},
                // AdminPracticeNoteController
                new Object[]{HttpMethod.GET, "/admin/practice-notes"},
                new Object[]{HttpMethod.GET, "/admin/practice-logs"},
                // AdminStudyRoomController
                new Object[]{HttpMethod.GET, "/admin/study-rooms"},
                new Object[]{HttpMethod.GET, "/admin/study-rooms/999999"},
                // AdminAnalysisController
                new Object[]{HttpMethod.GET, "/admin/analysis"},
                new Object[]{HttpMethod.GET, "/admin/analysis/daily-new-users?date=2026-01-01"},
                new Object[]{HttpMethod.GET, "/admin/analysis/daily-active-users?date=2026-01-01"},
                // AdminFeedbackController
                new Object[]{HttpMethod.GET, "/admin/feedbacks"},
                new Object[]{HttpMethod.GET, "/admin/feedbacks/999999"}
        );
    }

    private MockHttpServletRequestBuilder call(HttpMethod method, String uri) {
        return request(method, uri);
    }

    @Nested
    @DisplayName("일반 사용자")
    class AsMember {

        @ParameterizedTest(name = "{0} {1} 는 ROLE_MEMBER 에게 403")
        @MethodSource("com.aisip.OnO.backend.admin.security.AdminEndpointAuthorizationTest#adminEndpoints")
        @DisplayName("ROLE_MEMBER 로 관리자 엔드포인트를 호출하면 403 이고 본문이 새어 나가지 않는다")
        void memberIsForbidden(HttpMethod method, String uri) throws Exception {
            authenticateAs(member.getId());

            MvcResult result = mockMvc.perform(call(method, uri)).andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("%s %s 가 일반 사용자에게 뚫리면 전체 사용자 데이터가 노출된다", method, uri)
                    .isEqualTo(403);
            assertThat(result.getResponse().getContentAsString())
                    .as("차단됐다면 관리자 화면(HTML)이 렌더링돼서는 안 된다")
                    .doesNotContain("<html");
        }
    }

    @Nested
    @DisplayName("게스트 사용자")
    class AsGuest {

        @ParameterizedTest(name = "{0} {1} 는 ROLE_GUEST 에게 403")
        @MethodSource("com.aisip.OnO.backend.admin.security.AdminEndpointAuthorizationTest#adminEndpoints")
        @DisplayName("ROLE_GUEST 로 관리자 엔드포인트를 호출하면 403")
        void guestIsForbidden(HttpMethod method, String uri) throws Exception {
            authenticateAs(member.getId(), "ROLE_GUEST");

            assertThat(mockMvc.perform(call(method, uri)).andReturn().getResponse().getStatus())
                    .as("%s %s", method, uri)
                    .isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("인증 없는 요청")
    class AsAnonymous {

        @ParameterizedTest(name = "{0} {1} 는 비인증 요청에 401")
        @MethodSource("com.aisip.OnO.backend.admin.security.AdminEndpointAuthorizationTest#adminEndpoints")
        @DisplayName("토큰 없이 관리자 엔드포인트를 호출하면 401")
        void anonymousIsUnauthorized(HttpMethod method, String uri) throws Exception {
            clearAuthentication();

            assertThat(mockMvc.perform(call(method, uri)).andReturn().getResponse().getStatus())
                    .as("%s %s", method, uri)
                    .isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("관리자")
    class AsAdmin {

        @Test
        @DisplayName("ROLE_ADMIN 은 관리자 메인 화면을 볼 수 있다")
        void adminCanAccessMainPage() throws Exception {
            User admin = createAdminUser();
            authenticateAs(admin.getId(), "ROLE_ADMIN");

            assertThat(mockMvc.perform(call(HttpMethod.GET, "/admin/main")).andReturn().getResponse().getStatus())
                    .as("관리자까지 막히면 운영이 불가능하다")
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("관리자 컨트롤러에 새로 추가된 엔드포인트가 이 테스트의 목록에 빠져 있지 않다")
    void everyAdminMappingIsCoveredByThisTest() {
        long declaredHandlerCount = java.util.stream.Stream.of(
                        com.aisip.OnO.backend.admin.controller.AdminController.class,
                        com.aisip.OnO.backend.admin.controller.AdminUserController.class,
                        com.aisip.OnO.backend.admin.controller.AdminProblemController.class,
                        com.aisip.OnO.backend.admin.controller.AdminPracticeNoteController.class,
                        com.aisip.OnO.backend.admin.controller.AdminStudyRoomController.class,
                        com.aisip.OnO.backend.admin.controller.AdminAnalysisController.class,
                        com.aisip.OnO.backend.admin.controller.AdminFeedbackController.class
                )
                .flatMap(type -> java.util.Arrays.stream(type.getDeclaredMethods()))
                .filter(m -> java.util.Arrays.stream(m.getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping")))
                .count();

        assertThat(adminEndpoints().count())
                .as("관리자 엔드포인트를 추가했다면 adminEndpoints() 에도 반드시 넣어 권한 검증을 받게 해야 한다")
                .isEqualTo(declaredHandlerCount);
    }
}
