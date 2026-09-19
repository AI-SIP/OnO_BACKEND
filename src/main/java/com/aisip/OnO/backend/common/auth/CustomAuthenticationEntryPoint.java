package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import com.aisip.OnO.backend.common.response.CommonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 인증 실패 응답을 쓰지 않을 공개 경로. 접두사가 아니라 정확 매칭이다.
     *
     * <p>과거에는 {@code /api/auth} 접두사로 판정해서 보호 대상인
     * {@code POST /api/auth/logout} 까지 딸려 들어갔다. 그 결과 인증이 없어도
     * 401 이 아니라 <b>빈 200</b> 이 나가서, 앱은 로그아웃에 성공했다고 믿는데
     * 서버는 세션도 블랙리스트도 건드리지 않았다.
     *
     * <p>{@code /api/auth} 아래에서 공개여야 하는 것은 아직 토큰이 없는 상태에서
     * 부르는 세 가지뿐이다. 게스트 로그인, 소셜 로그인과 가입, 토큰 갱신이다.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/",
            "/robots.txt",
            "/home",
            "/login",
            "/grafana",
            "/prometheus",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/api/auth/signup/guest",
            "/api/auth/signup/member",
            "/api/auth/refresh"
    );

    /** 하위 경로 전체가 공개인 것들. 반드시 {@code /} 로 끝내서 접두사가 옆 경로를 먹지 않게 한다. */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/grafana/",
            "/prometheus/",
            "/swagger-ui/",
            "/v3/api-docs/"
    );

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        if (isPublicPath(request.getRequestURI())) {
            return;
        }

        ErrorCase errorCase = resolveErrorCase(request);

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCase.getHttpStatusCode());
        objectMapper.writeValue(response.getWriter(), CommonResponse.error(errorCase));
    }

    private boolean isPublicPath(String requestURI) {
        if (requestURI == null) {
            return false;
        }

        // 서블릿 컨테이너의 getRequestURI() 에는 쿼리스트링이 없지만,
        // 테스트나 다른 구현이 붙여 보내도 판정이 흔들리지 않게 잘라낸다.
        int queryIndex = requestURI.indexOf('?');
        String path = queryIndex < 0 ? requestURI : requestURI.substring(0, queryIndex);

        if (path.contains("/actuator/")) {
            return true;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private ErrorCase resolveErrorCase(HttpServletRequest request) {
        Object errorCase = request.getAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE);
        if (errorCase instanceof ErrorCase resolvedErrorCase) {
            return resolvedErrorCase;
        }
        return AuthErrorCase.AUTHENTICATION_FAILED;
    }
}
