package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)  // 401 Unauthorized
public class InvalidTokenException extends TokenException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
