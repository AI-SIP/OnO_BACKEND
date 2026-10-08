package com.aisip.OnO.backend.mcp.config;

import com.aisip.OnO.backend.mcp.auth.McpAdminTokenFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * {@code /mcp/**} 전용 보안 체인. 앱 JWT, 관리자 폼 로그인, 세션과 섞이지 않게 기본 체인보다 앞에 둔다.
 *
 * <p>MCP 클라이언트는 브라우저가 아니라 CORS 와 CSRF 를 쓰지 않는다. 인증은 관리자 MCP 토큰 하나뿐이고,
 * 실패하면 로그인 페이지로 보내지 않고 401 을 그대로 돌려준다.
 */
@Slf4j
@Configuration
public class McpSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http, @Value("${mcp.admin.token:}") String adminToken) throws Exception {
        McpAdminTokenFilter tokenFilter = new McpAdminTokenFilter(adminToken);
        if (!tokenFilter.isConfigured()) {
            log.warn("mcp.admin.token 이 비어 있어 /mcp/** 요청을 모두 401 로 막습니다.");
        }

        http.securityMatcher("/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .addFilterBefore(tokenFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized"))
                        .accessDeniedHandler((request, response, e) ->
                                reject(response, HttpServletResponse.SC_FORBIDDEN, "forbidden")));
        return http.build();
    }

    /**
     * sendError 를 쓰면 /error 로 다시 들어가 기본 체인의 응답 모양이 섞인다. 상태와 짧은 본문만 직접 쓴다.
     */
    private static void reject(HttpServletResponse response, int status, String error) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
