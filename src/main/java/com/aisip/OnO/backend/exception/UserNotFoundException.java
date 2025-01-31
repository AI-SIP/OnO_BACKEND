package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // HTTP 404 반환
public class UserNotFoundException extends RuntimeException {
    private static final String DEFAULT_MESSAGE = "사용자를 찾을 수 없습니다.";

    // 기본 메시지를 사용하는 생성자
    public UserNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    // userId를 포함한 메시지를 설정하는 생성자
    public UserNotFoundException(Long userId) {
        super("User ID: " + userId + "인 " + DEFAULT_MESSAGE);
    }

    // 사용자 정의 메시지를 설정할 수 있는 생성자
    public UserNotFoundException(String message) {
        super(message);
    }
}