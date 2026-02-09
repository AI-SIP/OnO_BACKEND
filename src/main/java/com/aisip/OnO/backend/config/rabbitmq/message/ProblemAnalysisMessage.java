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

    private int retryCount; // 재시도 횟수

    public ProblemAnalysisMessage(Long problemId) {
        this.problemId = problemId;
        this.retryCount = 0;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}