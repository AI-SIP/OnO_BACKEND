package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN) // HTTP 404 반환
public class ProblemRegisterException extends RuntimeException{
    public ProblemRegisterException(String message) {
        super(message);
    }
}
