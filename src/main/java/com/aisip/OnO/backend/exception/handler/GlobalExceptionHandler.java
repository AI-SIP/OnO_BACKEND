package com.aisip.OnO.backend.exception.handler;

import com.aisip.OnO.backend.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 📌 공통적으로 사용할 예외 응답 생성 메서드
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());  // 에러 발생 시간 추가
        body.put("status", status.value());          // HTTP 상태 코드
        body.put("error", status.getReasonPhrase()); // 상태 설명
        body.put("message", message);                // 예외 메시지

        return ResponseEntity.status(status).body(body);
    }

    // 📌 NOT FOUND (404) 예외 처리
    @ExceptionHandler({
            FolderNotFoundException.class,
            ProblemNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFoundExceptions(RuntimeException ex) {
        return createErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 📌 FORBIDDEN (403) 예외 처리
    @ExceptionHandler({
            ProblemRegisterException.class,
            UserNotAuthorizedException.class
    })
    public ResponseEntity<Map<String, Object>> handleForbiddenExceptions(RuntimeException ex) {
        return createErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // 📌 기타 모든 예외 처리 (예상하지 못한 예외)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }
}
