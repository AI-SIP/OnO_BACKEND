package com.aisip.OnO.backend.mcp;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.mcp.auth.McpAdminTokenFilter;
import com.aisip.OnO.backend.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MCP 관리자 엔드포인트 인증과 프로토콜")
class McpEndpointSecurityTest extends McpTestSupport {

    private static final Map<String, Object> INITIALIZE = Map.of(
            "protocolVersion", "2025-06-18",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "test", "version", "1"));

    @Autowired
    private JwtTokenizer jwtTokenizer;

    @Nested
    @DisplayName("인증")
    class Authentication {

        @Test
        @DisplayName("토큰이 없으면 401")
        void rejectsMissingToken() throws Exception {
            assertThat(rpc(null, "tools/list", null).getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("틀린 토큰이면 401")
        void rejectsWrongToken() throws Exception {
            assertThat(rpc("Bearer " + ADMIN_TOKEN + "x", "tools/list", null).getResponse().getStatus()).isEqualTo(401);
            assertThat(rpc(ADMIN_TOKEN, "tools/list", null).getResponse().getStatus())
                    .as("Bearer 접두사가 없으면 받지 않는다").isEqualTo(401);
        }

        @Test
        @DisplayName("앱 사용자 JWT 로는 들어올 수 없다. 관리자 JWT 도 마찬가지다")
        void rejectsAppJwt() throws Exception {
            User member = fixtures.createUser("member");
            String memberJwt = jwtTokenizer.createAccessToken(String.valueOf(member.getId()), Map.of("authority", Authority.ROLE_MEMBER));
            String adminJwt = jwtTokenizer.createAccessToken(String.valueOf(member.getId()), Map.of("authority", Authority.ROLE_ADMIN));

            assertThat(rpc(memberJwt, "tools/list", null).getResponse().getStatus()).isEqualTo(401);
            assertThat(rpc(adminJwt, "tools/list", null).getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("401 응답은 로그인 페이지로 보내지 않고 짧은 JSON 을 준다")
        void unauthorizedBodyIsJson() throws Exception {
            var response = rpc(null, "tools/list", null).getResponse();

            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"unauthorized\"}");
        }

        @Test
        @DisplayName("올바른 토큰이면 세션 쿠키 없이 통과한다")
        void acceptsTokenStatelessly() throws Exception {
            var response = rpc("Bearer " + ADMIN_TOKEN, "tools/list", null).getResponse();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getCookie("JSESSIONID")).isNull();
            assertThat(response.getHeader("Set-Cookie")).isNull();
        }

        @Test
        @DisplayName("MCP 토큰으로 관리자 페이지와 앱 API 에 들어갈 수 없다")
        void tokenDoesNotOpenOtherChains() throws Exception {
            mockMvc.perform(get("/admin/main").header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().is3xxRedirection());
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("토큰 필터")
    class TokenFilter {

        @Test
        @DisplayName("설정된 토큰이 비어 있으면 어떤 값으로도 인증하지 않는다")
        void blankConfigAuthenticatesNothing() throws Exception {
            for (String configured : new String[]{null, "", "   "}) {
                McpAdminTokenFilter filter = new McpAdminTokenFilter(configured);
                for (String header : new String[]{"Bearer ", "Bearer", "Bearer  ", null}) {
                    SecurityContextHolder.clearContext();
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", ENDPOINT);
                    if (header != null) {
                        request.addHeader("Authorization", header);
                    }
                    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

                    assertThat(filter.isConfigured()).isFalse();
                    assertThat(SecurityContextHolder.getContext().getAuthentication())
                            .as("설정 [%s], 헤더 [%s]", configured, header).isNull();
                }
            }
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("설정값 앞뒤 공백은 무시한다")
        void trimsConfiguredToken() throws Exception {
            McpAdminTokenFilter filter = new McpAdminTokenFilter("  abc  ");
            MockHttpServletRequest request = new MockHttpServletRequest("POST", ENDPOINT);
            request.addHeader("Authorization", "Bearer abc");

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(Object::toString).containsExactly("ROLE_ADMIN");
            SecurityContextHolder.clearContext();
        }
    }

    @Nested
    @DisplayName("프로토콜")
    class Protocol {

        @Test
        @DisplayName("initialize 는 서버 이름과 도구 기능만 알린다")
        void initialize() throws Exception {
            JsonNode result = rpcResult("initialize", INITIALIZE);

            assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ono-admin");
            assertThat(result.path("capabilities").has("tools")).isTrue();
            assertThat(result.path("capabilities").has("resources")).isFalse();
            assertThat(result.path("capabilities").has("prompts")).isFalse();
        }

        @Test
        @DisplayName("도구는 8개이고 전부 조회 전용으로 표시된다")
        void toolsAreReadOnly() throws Exception {
            JsonNode tools = rpcResult("tools/list", null).path("tools");

            List<String> names = new ArrayList<>();
            tools.forEach(tool -> {
                names.add(tool.path("name").asText());
                assertThat(tool.path("annotations").path("readOnlyHint").asBoolean())
                        .as("%s readOnlyHint", tool.path("name").asText()).isTrue();
                assertThat(tool.path("annotations").path("destructiveHint").asBoolean())
                        .as("%s destructiveHint", tool.path("name").asText()).isFalse();
                assertThat(tool.path("description").asText()).isNotBlank();
            });
            assertThat(names).containsExactlyInAnyOrder(
                    "get_service_stats", "get_today_overview", "search_users", "get_user_activity",
                    "list_problems", "list_feedbacks", "list_study_rooms", "get_study_room");
        }
    }
}
