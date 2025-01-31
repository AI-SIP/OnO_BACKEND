package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // HTTP 404 반환
public class ProblemNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "오답노트를 찾을 수 없습니다.";

    public ProblemNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public ProblemNotFoundException(Long problemId) {
        super("Problem ID: " + problemId + "인 " + DEFAULT_MESSAGE);
    }
    public ProblemNotFoundException(String message) {
        super(message);
    }
}
