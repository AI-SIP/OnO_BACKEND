package com.aisip.OnO.backend.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);  // 403 Forbidden 상태 코드
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"errorCode\":\"403\",\n\"message\":\"접근 권한이 없습니다.\"}");  // 사용자 정의 메시지
    }
}

