package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.common.exception.HandledFailure;

/**
 * 재시도로 해결되지 않는 분석 실패(예: 이미지 판독 불가) 예외
 *
 * ProblemAnalysisConsumer 가 잡아서 재큐잉 없이 ACK 하고, 상태도 FAILED 로 정리한다.
 * 즉 여기까지가 정상 처리 경로라 HandledFailure 를 붙여 error 로그 대상에서 뺀다.
 */
public class NonRetryableAnalysisException extends RuntimeException implements HandledFailure {

    public NonRetryableAnalysisException(String message) {
        super(message);
    }

    public NonRetryableAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
