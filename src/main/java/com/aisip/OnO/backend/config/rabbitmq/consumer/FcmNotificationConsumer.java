package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.FcmNotificationMessage;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FCM 푸시 알림 메시지 Consumer
 * - RabbitMQ에서 메시지를 받아 FCM 전송 처리
 * - 실패 시 자동으로 DLQ로 전송 (RabbitMQ가 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmNotificationConsumer {

    private final FcmTokenRepository fcmTokenRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    /**
     * FCM 푸시 알림 메시지 수신 및 처리
     * - concurrency: 동시 처리 개수 (5-15개, FCM Rate Limit 고려)
     * - 예외 발생 시 자동으로 재시도 → 실패하면 DLQ로 이동
     */
    @RabbitListener(queues = RabbitMQConfig.FCM_NOTIFICATION_QUEUE, concurrency = "5-15")
    public void handleNotificationMessage(FcmNotificationMessage message) {
        log.info("[FCM Notification Consumer] 메시지 수신 - userId: {}, title: {}, retryCount: {}",
                message.getUserId(), message.getTitle(), message.getRetryCount());

        try {
            // 사용자의 모든 FCM 토큰 조회
            List<FcmToken> userFcmTokenList = fcmTokenRepository.findAllByUserId(message.getUserId());

            if (userFcmTokenList.isEmpty()) {
                log.warn("[FCM Notification Consumer] FCM 토큰 없음 - userId: {}", message.getUserId());
                return; // 토큰 없으면 스킵 (정상 처리)
            }

            // 각 디바이스로 알림 전송
            int successCount = 0;
            int failCount = 0;

            for (FcmToken fcmToken : userFcmTokenList) {
                try {
                    sendToDevice(fcmToken.getToken(), message);
                    successCount++;
                } catch (Exception e) {
                    log.warn("[FCM Notification Consumer] 디바이스 전송 실패 - userId: {}, token: {}, error: {}",
                            message.getUserId(), fcmToken.getToken(), e.getMessage());
                    failCount++;
                }
            }

            log.info("[FCM Notification Consumer] 전송 완료 - userId: {}, 성공: {}, 실패: {}",
                    message.getUserId(), successCount, failCount);

            // 모든 디바이스 전송 실패 시 예외 발생 (재시도)
            if (successCount == 0 && failCount > 0) {
                throw new RuntimeException("모든 디바이스 FCM 전송 실패 - userId: " + message.getUserId());
            }

        } catch (Exception e) {
            log.error("[FCM Notification Consumer] 알림 전송 실패 - userId: {}, retryCount: {}, error: {}",
                    message.getUserId(), message.getRetryCount(), e.getMessage());

            // 예외를 던지면 RabbitMQ가 자동으로 재시도 or DLQ로 전송
            throw new RuntimeException("FCM 푸시 알림 전송 실패: " + message.getUserId(), e);
        }
    }

    /**
     * 특정 디바이스로 FCM 전송
     */
    private void sendToDevice(String token, FcmNotificationMessage message) throws FirebaseMessagingException {
        Message fcmMessage = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(message.getTitle())
                        .setBody(message.getBody())
                        .build())
                .putAllData(message.getData())
                .build();

        String messageId = firebaseMessaging.send(fcmMessage);
        log.debug("[FCM Notification Consumer] FCM 전송 성공 - messageId: {}", messageId);
    }

    /**
     * DLQ 모니터링 및 알림 전송
     * - 모든 재시도 실패 후 DLQ로 들어온 메시지 처리
     * - Discord 알림 전송하여 관리자에게 수동 처리 요청
     */
    @RabbitListener(queues = RabbitMQConfig.FCM_NOTIFICATION_DLQ)
    public void handleNotificationDLQ(FcmNotificationMessage message) {
        log.error("[FCM Notification DLQ] 최종 실패 메시지 - userId: {}, title: {}, retryCount: {}",
                message.getUserId(), message.getTitle(), message.getRetryCount());

        // Discord 알림 전송
        String errorTitle = String.format("🚨 FCM 푸시 알림 최종 실패 (DLQ)");
        String errorDetails = String.format(
                "**User ID:** %d\n**Title:** %s\n**Body:** %s\n**Retry Count:** %d\n\n" +
                "모든 재시도가 실패했습니다. FCM 토큰을 확인하거나 수동으로 알림을 재전송해주세요.",
                message.getUserId(),
                message.getTitle(),
                message.getBody(),
                message.getRetryCount()
        );

        try {
            discordWebhookNotificationService.sendErrorNotification(
                    "RabbitMQ DLQ - FCM Notification",
                    errorDetails,
                    "ERROR",
                    errorTitle
            );
            log.info("[FCM Notification DLQ] Discord 알림 전송 완료");
        } catch (Exception e) {
            log.error("[FCM Notification DLQ] Discord 알림 전송 실패: {}", e.getMessage());
        }
    }
}