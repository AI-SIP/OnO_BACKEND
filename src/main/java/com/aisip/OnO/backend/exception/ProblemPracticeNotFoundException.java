package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // HTTP 404 반환
public class ProblemPracticeNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "복습 리스트를 찾을 수 없습니다.";

    public ProblemPracticeNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public ProblemPracticeNotFoundException(Long practiceId) {
        super("Practice ID: " + practiceId + "인 " + DEFAULT_MESSAGE);
    }

    public ProblemPracticeNotFoundException(String message) {
        super(message);
    }
}
