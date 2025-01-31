package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN) // HTTP 404 반환
public class ProblemRegisterException extends RuntimeException{

    private static final String DEFAULT_MESSAGE = "문제 등록 과정에서 오류가 발생했습니다.";

    public ProblemRegisterException() {
        super(DEFAULT_MESSAGE);
    }

    public ProblemRegisterException(String message) {
        super(message);
    }
}
