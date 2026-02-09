package com.aisip.OnO.backend.config.rabbitmq.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * S3 파일 삭제 메시지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class S3DeleteMessage implements Serializable {

    private String imageUrl;

    private Long problemId; // 디버깅/로깅용

    private int retryCount; // 재시도 횟수

    public S3DeleteMessage(String imageUrl, Long problemId) {
        this.imageUrl = imageUrl;
        this.problemId = problemId;
        this.retryCount = 0;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}