package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)  // 400 Bad Request
public class MissingTokenException extends TokenException {
    public MissingTokenException(String message) {
        super(message);
    }
}
