package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static com.aisip.OnO.backend.auth.service.JwtTokenizer.BEARER_PREFIX;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTH_ERROR_CASE_ATTRIBUTE = "authErrorCase";

    private final JwtTokenizer jwtTokenizer;
    private final RedisTokenService redisTokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // JWT 필터를 건너뛸 경로들
        return path.contains("/actuator/") ||
                 path.equals("/grafana") ||
                 path.startsWith("/grafana/") ||
                 path.equals("/prometheus") ||
                 path.startsWith("/prometheus/") ||
                 path.equals("/") ||
                 path.equals("/robots.txt") ||
                 path.equals("/home") ||
                 path.startsWith("/images/") ||
                 path.equals("/perform-login") ||
                 path.equals("/login") ||
                 path.startsWith("/css/") ||
                 path.startsWith("/js/") ||
                 path.startsWith("/swagger-ui/") ||
                 path.startsWith("/v3/api-docs/");
      }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        String accessToken = null;
        if(bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            accessToken = bearerToken.substring(7).trim();
        }

        if(accessToken != null) {
            try {
                // 1. 토큰 검증 (만료 여부 먼저 확인)
                jwtTokenizer.validateAccessToken(accessToken);

                // 2. 블랙리스트 체크 (로그아웃된 토큰인지 확인)
                if (redisTokenService.isBlacklisted(accessToken)) {
                    request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.INVALID_ACCESS_TOKEN);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 3. 인증 정보 설정
                Claims claims = jwtTokenizer.getClaimsFromAccessToken(accessToken);
                Long userId = Long.valueOf(claims.getSubject());
                Authority authority = Authority.valueOf(claims.get("authority", String.class));

                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority.name()));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("userId", String.valueOf(userId));
            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.ACCESS_TOKEN_EXPIRED);
            } catch (Exception e) {
                request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.AUTHENTICATION_FAILED);
            }
        }

        filterChain.doFilter(request, response);
    }
}
