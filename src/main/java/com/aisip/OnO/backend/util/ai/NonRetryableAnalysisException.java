package com.aisip.OnO.backend.util.ai;

/**
 * 재시도로 해결되지 않는 분석 실패(예: 이미지 판독 불가) 예외
 */
public class NonRetryableAnalysisException extends RuntimeException {

    public NonRetryableAnalysisException(String message) {
        super(message);
    }

    public NonRetryableAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
