package com.aisip.OnO.backend.config.rabbitmq.producer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * S3 파일 삭제 메시지 Producer
 * - 메시지를 RabbitMQ로 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeleteProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * S3 파일 삭제 메시지 전송
     * @param imageUrl 삭제할 이미지 URL
     * @param problemId 문제 ID (로깅용)
     */
    public void sendDeleteMessage(String imageUrl, Long problemId) {
        try {
            S3DeleteMessage message = new S3DeleteMessage(imageUrl, problemId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FILE_EXCHANGE,
                    RabbitMQConfig.S3_DELETE_ROUTING_KEY,
                    message
            );

            log.info("[S3 Delete Producer] 메시지 전송 성공 - imageUrl: {}, problemId: {}", imageUrl, problemId);
        } catch (Exception e) {
            log.error("[S3 Delete Producer] 메시지 전송 실패 - imageUrl: {}, problemId: {}, error: {}",
                    imageUrl, problemId, e.getMessage(), e);
            // 메시지 전송 실패해도 DB 삭제는 완료되므로 예외를 던지지 않음
        }
    }

    /**
     * 여러 파일 삭제 메시지 일괄 전송
     * @param imageUrls 삭제할 이미지 URL 목록
     * @param problemId 문제 ID (로깅용)
     */
    public void sendBulkDeleteMessages(Iterable<String> imageUrls, Long problemId) {
        imageUrls.forEach(imageUrl -> sendDeleteMessage(imageUrl, problemId));
    }
}