package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.DiscordWebhookMessage;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class DiscordWebhookConsumer {

    private final RestTemplate restTemplate;

    @Value("${discord.webhook-url}")
    private String webhookUrl;

    public DiscordWebhookConsumer() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @RabbitListener(queues = RabbitMQConfig.DISCORD_WEBHOOK_QUEUE, concurrency = "1-3")
    public void handleWebhookMessage(DiscordWebhookMessage message) {
        DiscordWebhookPayload payload = message.getPayload();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload, headers), String.class);
            log.info("Discord webhook 전송 완료: {}", payload.embeds().get(0).title());
        } catch (Exception e) {
            // 일시적 실패(read timeout 등)는 RabbitMQ 가 재시도하고, 최종 실패는 DLQ 에서 error 로 남는다.
            // 여기서 error 로 올리면 재시도로 성공한 건까지 Sentry 에 쌓인다 (Sentry JAVA-SPRING-BOOT-5C).
            // 예외 메시지에 웹훅 URL(토큰 포함)이 그대로 들어 있어 메시지 대신 예외 타입만 남긴다.
            log.warn("Discord webhook 전송 실패, 재시도 예정 - exceptionType: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Discord webhook 전송 실패", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.DISCORD_WEBHOOK_DLQ)
    public void handleWebhookDLQ(DiscordWebhookMessage message) {
        DiscordWebhookPayload payload = message.getPayload();
        String title = payload.embeds().isEmpty() ? "(제목 없음)" : payload.embeds().get(0).title();
        log.error("Discord webhook DLQ — 최종 전송 실패, 수동 확인 필요. title={}", title);
    }
}
