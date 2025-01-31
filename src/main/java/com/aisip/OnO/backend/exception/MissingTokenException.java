package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)  // 400 Bad Request
public class MissingTokenException extends TokenException {

    private static final String DEFAULT_MESSAGE = "잘못된 토큰 형식입니다.";

    public MissingTokenException() {
        super(DEFAULT_MESSAGE);
    }

    public MissingTokenException(String message) {
        super(message);
    }
}
