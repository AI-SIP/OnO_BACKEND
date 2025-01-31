package com.aisip.OnO.backend.exception;


public class TokenException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "토큰 인증 과정에서 오류가 발생했습니다.";

    public TokenException() {
        super(DEFAULT_MESSAGE);
    }
    public TokenException(String message) {
        super(message);
    }
}
