package com.aisip.OnO.backend.config.rabbitmq.producer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.FcmNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FCM 푸시 알림 메시지 Producer
 * - 메시지를 RabbitMQ로 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * FCM 푸시 알림 메시지 전송
     * @param userId 알림을 받을 사용자 ID
     * @param title 알림 제목
     * @param body 알림 내용
     * @param data 추가 데이터
     */
    public void sendNotificationMessage(Long userId, String title, String body, Map<String, String> data) {
        try {
            FcmNotificationMessage message = new FcmNotificationMessage(userId, title, body, data);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.FCM_NOTIFICATION_ROUTING_KEY,
                    message
            );

            log.info("RabbitMQ message sent - exchange: {}, routingKey: {}, operation: {}, userId: {}",
                    RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.FCM_NOTIFICATION_ROUTING_KEY,
                    "fcm_notification", userId);
        } catch (Exception e) {
            log.error("RabbitMQ message send failed - exchange: {}, routingKey: {}, operation: {}, userId: {}, error: {}",
                    RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.FCM_NOTIFICATION_ROUTING_KEY,
                    "fcm_notification", userId, e.getMessage(), e);
            // 알림 전송 실패해도 예외를 던지지 않음 (알림은 선택적 기능)
        }
    }
}
