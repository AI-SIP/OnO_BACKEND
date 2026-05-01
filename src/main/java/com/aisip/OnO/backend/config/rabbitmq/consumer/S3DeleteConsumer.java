package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * S3 파일 삭제 메시지 Consumer
 * - RabbitMQ에서 메시지를 받아 S3 파일 삭제 처리
 * - 실패 시 자동으로 DLQ로 전송 (RabbitMQ가 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeleteConsumer {

    private final FileUploadService fileUploadService;
    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    /**
     * S3 파일 삭제 메시지 수신 및 처리
     * - concurrency: 동시 처리 개수 (3~10개 스레드)
     * - 예외 발생 시 자동으로 재시도 → 실패하면 DLQ로 이동
     */
    @RabbitListener(queues = RabbitMQConfig.S3_DELETE_QUEUE, concurrency = "3-10")
    public void handleS3DeleteMessage(S3DeleteMessage message) {
        log.info("[S3 Delete Consumer] 메시지 수신 - problemId: {}, retryCount: {}",
                message.getProblemId(), message.getRetryCount());

        try {
            // S3 파일 삭제 실행
            fileUploadService.deleteImageFileFromS3(message.getImageUrl());

            log.info("[S3 Delete Consumer] 삭제 성공 - problemId: {}", message.getProblemId());

        } catch (Exception e) {
            log.error("[S3 Delete Consumer] 삭제 실패 - problemId: {}, retryCount: {}, error: {}",
                    message.getProblemId(), message.getRetryCount(), e.getMessage());

            // 예외를 던지면 RabbitMQ가 자동으로 재시도 or DLQ로 전송
            throw new RuntimeException("S3 파일 삭제 실패 - problemId: " + message.getProblemId(), e);
        }
    }

    /**
     * DLQ 모니터링 및 알림 전송
     * - 모든 재시도 실패 후 DLQ로 들어온 메시지 처리
     * - Discord 알림 전송하여 관리자에게 수동 처리 요청
     */
    @RabbitListener(queues = RabbitMQConfig.S3_DELETE_DLQ)
    public void handleS3DeleteDLQ(S3DeleteMessage message) {
        String maskedObjectKey = maskS3ObjectKey(message.getImageUrl());

        log.error("[S3 Delete DLQ] 최종 실패 메시지 - problemId: {}, objectKey: {}, retryCount: {}",
                message.getProblemId(), maskedObjectKey, message.getRetryCount());

        // Discord 알림 전송
        String errorTitle = String.format("🚨 S3 파일 삭제 최종 실패 (DLQ)");
        String errorDetails = String.format(
                "**Problem ID:** %d\n**Object Key:** %s\n**Retry Count:** %d\n\n" +
                "모든 재시도가 실패했습니다. 수동으로 S3에서 파일을 삭제하거나 메시지를 재처리해주세요.",
                message.getProblemId(),
                maskedObjectKey,
                message.getRetryCount()
        );

        try {
            discordWebhookNotificationService.sendErrorNotification(
                    "RabbitMQ DLQ - S3 Delete",
                    errorDetails,
                    "ERROR",
                    errorTitle
            );
            log.info("[S3 Delete DLQ] Discord 알림 전송 완료");
        } catch (Exception e) {
            log.error("[S3 Delete DLQ] Discord 알림 전송 실패: {}", e.getMessage());
        }
    }

    private String maskS3ObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "unknown";
        }

        String splitStr = ".com/";
        int keyStartIndex = imageUrl.lastIndexOf(splitStr);
        String objectKey = keyStartIndex >= 0
                ? imageUrl.substring(keyStartIndex + splitStr.length())
                : imageUrl;

        if (objectKey.length() <= 16) {
            return "***";
        }

        return objectKey.substring(0, 8) + "***" + objectKey.substring(objectKey.length() - 8);
    }
}
