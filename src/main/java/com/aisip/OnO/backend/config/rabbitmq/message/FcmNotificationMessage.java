package com.aisip.OnO.backend.config.rabbitmq.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * FCM 푸시 알림 메시지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FcmNotificationMessage implements Serializable {

    private Long userId;
    private String title;
    private String body;
    private Map<String, String> data;
    private int retryCount;

    public FcmNotificationMessage(Long userId, String title, String body, Map<String, String> data) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.data = data;
        this.retryCount = 0;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
