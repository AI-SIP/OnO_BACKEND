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

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        String requestURI = request.getRequestURI();
        if(requestURI.contains("/actuator/") ||
            requestURI.equals("/grafana") ||
            requestURI.startsWith("/grafana/") ||
            requestURI.equals("/prometheus") ||
            requestURI.startsWith("/prometheus/") ||
            requestURI.startsWith("/api/auth") ||
                requestURI.equals("/") ||
                requestURI.equals("/robots.txt") ||
                requestURI.equals("/home") ||
                requestURI.startsWith("/login") ||
                requestURI.startsWith("/swagger-ui") ||
                requestURI.startsWith("/v3/api-docs")
        ) {
            return;
        }
        ErrorCase errorCase = resolveErrorCase(request);

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCase.getHttpStatusCode());
        objectMapper.writeValue(response.getWriter(), CommonResponse.error(errorCase));
    }

    private ErrorCase resolveErrorCase(HttpServletRequest request) {
        Object errorCase = request.getAttribute(JwtTokenFilter.AUTH_ERROR_CASE_ATTRIBUTE);
        if (errorCase instanceof ErrorCase resolvedErrorCase) {
            return resolvedErrorCase;
        }
        return AuthErrorCase.AUTHENTICATION_FAILED;
    }
}
