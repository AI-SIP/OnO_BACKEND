package com.aisip.OnO.backend.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)  // 401 Unauthorized
public class InvalidTokenException extends TokenException {

    private static final String DEFAULT_MESSAGE = "사용할 수 없는 토큰입니다.";

    public InvalidTokenException() {
        super(DEFAULT_MESSAGE);
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
