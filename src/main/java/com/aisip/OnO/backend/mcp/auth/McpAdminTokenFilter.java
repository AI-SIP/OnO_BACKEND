package com.aisip.OnO.backend.mcp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * {@code Authorization: Bearer <MCP 관리자 토큰>} 이 설정값과 같을 때만 관리자로 인증한다.
 *
 * <p>토큰이 비어 있으면 아무 요청도 인증하지 않는다. 배포 설정에서 {@code MCP_ADMIN_TOKEN} 을 빼먹었을 때
 * 엔드포인트가 열리는 대신 401 로 닫혀 있게 하려는 것이다. 비교는 길이가 같을 때 걸리는 시간이 값에 따라
 * 달라지지 않도록 {@link MessageDigest#isEqual} 로 한다.
 *
 * <p>빈으로 등록하지 않는다. 등록하면 Spring Boot 가 모든 요청에 도는 서블릿 필터로도 걸어 버린다.
 */
public class McpAdminTokenFilter extends OncePerRequestFilter {

    static final String PRINCIPAL = "mcp-admin";

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public McpAdminTokenFilter(String configuredToken) {
        String token = configuredToken == null ? "" : configuredToken.trim();
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isConfigured() {
        return expectedToken.length > 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isConfigured() && matches(request.getHeader("Authorization"))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = header.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expectedToken);
    }
}
