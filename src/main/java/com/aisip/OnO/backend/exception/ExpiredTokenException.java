package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)  // 401 Unauthorized
public class ExpiredTokenException extends TokenException {
    public ExpiredTokenException(String message) {
        super(message);
    }
}
