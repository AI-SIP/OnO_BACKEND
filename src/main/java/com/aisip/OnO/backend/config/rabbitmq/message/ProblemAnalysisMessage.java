package com.aisip.OnO.backend.config.rabbitmq.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * GPT 문제 분석 메시지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProblemAnalysisMessage implements Serializable {

    private Long problemId;

    /**
     * 사용하지 않는다. 항상 0 이다.
     * 재시도는 리스너 컨테이너가 같은 메시지로 다시 호출하는 방식이라 본문 값을 올려도 다음 시도에 전달되지 않는다.
     * 시도 횟수는 {@link com.aisip.OnO.backend.config.rabbitmq.RabbitRetryAttempts} 로 읽는다.
     * 메시지 JSON 모양을 바꾸지 않으려고 남겨 둔다. 롤링 배포 중 구버전과 신버전이 섞여도 같은 모양을 주고받는다.
     */
    private int retryCount;

    public ProblemAnalysisMessage(Long problemId) {
        this.problemId = problemId;
        this.retryCount = 0;
    }
}