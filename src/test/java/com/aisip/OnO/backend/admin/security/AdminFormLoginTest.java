package com.aisip.OnO.backend.admin.security;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 폼 로그인({@code admin.identifier} / {@code admin.password} 설정 기반) 검증.
 *
 * <p>이 경로가 뚫리면 관리자 화면 전체가 열린다. 성공 경로 하나보다
 * <b>실패해야 하는 입력이 전부 실패하는지</b>가 중요하다.
 */
@DisplayName("관리자 폼 로그인")
class AdminFormLoginTest extends AdminTestSupport {

    private static final String LOGIN_URL = "/perform_login";
    private static final String SUCCESS_URL = "http://localhost:8080/admin/main";
    private static final String FAILURE_URL = "http://localhost:8080/login?error";

    @BeforeEach
    void clearContext() {
        clearAuthentication();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                        .param("username", username)
                        .param("password", password))
                .andReturn();
    }

    @Nested
    @DisplayName("성공")
    class Success {

        @Test
        @DisplayName("설정된 관리자 자격증명으로 로그인하면 관리자 메인으로 보내고 ROLE_ADMIN 토큰을 발급한다")
        void issuesAdminTokenOnValidCredentials() throws Exception {
            User admin = createAdminUser();

            MvcResult result = login(adminIdentifier, adminPassword);

            assertThat(result.getResponse().getStatus()).isEqualTo(302);
            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(SUCCESS_URL);
            assertThat(result.getResponse().getHeader("Authorization"))
                    .as("성공 핸들러가 액세스 토큰을 헤더로 내려줘야 관리자 화면이 API 를 호출할 수 있다")
                    .isNotNull()
                    .startsWith("Bearer ");
            assertThat(admin.getId()).isNotNull();
        }

        /**
         * 로그인 성공 헤더가 그대로 인증에 쓰일 수 있어야 한다.
         *
         * <p>{@code JwtTokenizer.createAccessToken} 은 이미 {@code "Bearer "} 접두사를 붙여 돌려주는데
         * {@code SecurityConfig} 의 로그인 성공 핸들러가 한 번 더 붙여 {@code "Bearer Bearer eyJ..."} 를
         * 내려주고 있었다. {@code JwtTokenFilter} 는 앞 7글자만 떼므로 파싱에 실패해 401 이 됐고,
         * 결국 로그인 성공 응답의 헤더를 그대로 쓰면 인증되지 않았다.
         * 접두사를 프론트에 내려주는 토큰 형식이 이미 계약이라 createAccessToken 은 그대로 두고
         * 중복해서 붙이던 쪽을 고쳤다.
         */
        @Test
        @DisplayName("성공 응답의 Authorization 헤더에 Bearer 가 한 번만 붙는다")
        void authorizationHeaderIsPrefixedOnce() throws Exception {
            createAdminUser();

            String header = login(adminIdentifier, adminPassword).getResponse().getHeader("Authorization");

            assertThat(header)
                    .as("접두사가 두 번 붙으면 필터가 토큰을 파싱하지 못한다")
                    .startsWith("Bearer ")
                    .doesNotStartWith("Bearer Bearer ");
        }

        @Test
        @DisplayName("로그인 성공 헤더를 그대로 쓰면 관리자 권한으로 동작한다")
        void issuedTokenOpensAdminPage() throws Exception {
            createAdminUser();

            String usableToken = login(adminIdentifier, adminPassword).getResponse().getHeader("Authorization");

            mockMvc.perform(post("/admin/user/999999/level")
                            .header("Authorization", usableToken)
                            .param("levelType", "attendance")
                            .param("levelValue", "1")
                            .param("pointValue", "0"))
                    // 404 = 권한 검사를 통과해 컨트롤러까지 들어갔고, 없는 사용자라 거절된 것.
                    // 토큰 내용은 ROLE_ADMIN 으로 올바르게 만들어진다. 문제는 접두사뿐이다.
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("비밀번호가 틀리면 로그인 실패 페이지로 되돌린다")
        void rejectsWrongPassword() throws Exception {
            createAdminUser();

            MvcResult result = login(adminIdentifier, adminPassword + "-틀림");

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(FAILURE_URL);
            assertThat(result.getResponse().getHeader("Authorization"))
                    .as("실패했는데 토큰이 나가면 그대로 관리자 권한이 넘어간다")
                    .isNull();
        }

        @Test
        @DisplayName("존재하지 않는 관리자 계정은 로그인 실패 페이지로 되돌린다")
        void rejectsUnknownIdentifier() throws Exception {
            MvcResult result = login("없는-관리자", adminPassword);

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(FAILURE_URL);
            assertThat(result.getResponse().getHeader("Authorization")).isNull();
        }

        @Test
        @DisplayName("빈 아이디와 빈 비밀번호는 로그인 실패로 처리한다")
        void rejectsEmptyCredentials() throws Exception {
            createAdminUser();

            MvcResult result = login("", "");

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(FAILURE_URL);
            assertThat(result.getResponse().getHeader("Authorization")).isNull();
        }

        @Test
        @DisplayName("비밀번호만 비우면 로그인 실패로 처리한다")
        void rejectsEmptyPassword() throws Exception {
            createAdminUser();

            MvcResult result = login(adminIdentifier, "");

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(FAILURE_URL);
        }

        @Test
        @DisplayName("일반 소셜 로그인 사용자의 identifier 로는 관리자 로그인을 할 수 없다")
        void rejectsSocialLoginUser() throws Exception {
            User member = fixtures.createUser();

            MvcResult result = login(member.getIdentifier(), "아무비밀번호");

            assertThat(result.getResponse().getRedirectedUrl())
                    .as("비밀번호가 없는 사용자가 관리자로 로그인되면 전체 데이터가 열린다")
                    .isEqualTo(FAILURE_URL);
            assertThat(result.getResponse().getHeader("Authorization")).isNull();
        }

        @Test
        @DisplayName("로그인에 실패하면 관리자 화면 접근도 계속 막힌다")
        void keepsAdminPageClosedAfterFailedLogin() throws Exception {
            createAdminUser();
            login(adminIdentifier, "틀린비밀번호");

            mockMvc.perform(post("/admin/user/1/level")
                            .param("levelType", "attendance")
                            .param("levelValue", "1")
                            .param("pointValue", "0"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("로그인 페이지 자체는 인증 없이 열린다")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login"))
                .andExpect(status().isOk())
                .andExpect(redirectedUrl(null));
    }
}
