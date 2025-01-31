package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN) // HTTP 404 반환
public class UserNotAuthorizedException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "유저 인증 과정에서 오류가 발생했습니다.";

    public UserNotAuthorizedException() {
        super(DEFAULT_MESSAGE);
    }
    public UserNotAuthorizedException(String message) {
        super(message);
    }
}