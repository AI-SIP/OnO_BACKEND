package com.aisip.OnO.backend.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        String requestURI = request.getRequestURI();
        if(requestURI.startsWith("/actuator/health") ||
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
        String errorMessage = (String) request.getAttribute("errorMessage");

        if(errorMessage == null)
            errorMessage = "인증이 실패했습니다.";

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 UNAUTHORIZED 상태 코드
        response.getWriter().write("{\"errorCode\":\"401\",\n\"message\":\"" + errorMessage + "\"}");
    }

}
