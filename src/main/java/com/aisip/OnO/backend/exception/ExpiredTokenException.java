package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)  // 401 Unauthorized
public class ExpiredTokenException extends TokenException {

    private static final String DEFAULT_MESSAGE = "이미 만료된 토큰입니다.";

    public ExpiredTokenException() {
        super(DEFAULT_MESSAGE);
    }

    public ExpiredTokenException(String message) {
        super(message);
    }
}
