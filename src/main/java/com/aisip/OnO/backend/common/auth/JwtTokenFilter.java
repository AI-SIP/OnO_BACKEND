package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.service.JwtTokenizer;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import io.jsonwebtoken.Claims;
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

                // 3. 클레임 해석
                Claims claims = jwtTokenizer.getClaimsFromAccessToken(accessToken);
                Long userId = Long.valueOf(claims.getSubject());
                Authority authority = Authority.valueOf(claims.get("authority", String.class));

                // 4. 탈퇴한 계정인지 확인
                // 액세스 토큰은 서명과 만료만으로 통과하므로, 탈퇴한 뒤에도 만료 전(최대 30분)까지는
                // 사용자 존재를 확인하지 않는 엔드포인트가 그대로 동작한다. 실제로 탈퇴 계정의 토큰으로
                // POST /api/fcm/token 이 200 이 나면서 지웠던 fcm_token 행이 되살아났다(#300).
                // 매 요청마다 DB 로 사용자 존재를 확인하는 대신, 탈퇴 시점에 사용자 단위 블랙리스트를
                // 심어 두고 여기서 확인한다. 아래 블랙리스트 조회와 같은 Redis 경로라 DB 부하가 늘지 않는다.
                if (redisTokenService.isUserBlacklisted(userId)) {
                    request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.INVALID_ACCESS_TOKEN);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 5. 인증 정보 설정
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority.name()));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("userId", String.valueOf(userId));
                MDC.put("authority", authority.name());
            } catch (ApplicationException e) {
                // JwtTokenizer 가 ExpiredJwtException 을 ApplicationException 으로 바꿔 던지므로
                // 여기서 ExpiredJwtException 을 잡으면 영영 걸리지 않는다(과거 만료 토큰이 1005 대신
                // 1007 로 나가 프론트의 토큰 갱신 대신 강제 로그아웃이 발동하던 원인).
                request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, e.getErrorCase());
            } catch (Exception e) {
                request.setAttribute(AUTH_ERROR_CASE_ATTRIBUTE, AuthErrorCase.AUTHENTICATION_FAILED);
            }
        }

        filterChain.doFilter(request, response);
    }
}
