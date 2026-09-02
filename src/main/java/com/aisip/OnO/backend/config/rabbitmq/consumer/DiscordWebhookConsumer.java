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
        // 본문 없는 메시지는 재시도해도 보낼 것이 없다. 재시도/DLQ 없이 종료(ACK).
        if (payload == null) {
            log.warn("Discord webhook 메시지에 payload 가 없어 건너뜀. dedupKey={}", message.getDedupKey());
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            log.error("Discord webhook 전송 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Discord webhook 전송 실패", e);
        }

        // 전송 성공 후의 로깅은 try 밖에서 한다. embeds 가 비어 있을 때 나던 IndexOutOfBoundsException 이
        // catch 로 잡혀 "전송 실패"로 둔갑하면, 이미 나간 웹훅이 재시도로 중복 발송된다.
        log.info("Discord webhook 전송 완료: {}", extractTitle(payload));
    }

    @RabbitListener(queues = RabbitMQConfig.DISCORD_WEBHOOK_DLQ)
    public void handleWebhookDLQ(DiscordWebhookMessage message) {
        log.error("Discord webhook DLQ — 최종 전송 실패, 수동 확인 필요. title={}",
                extractTitle(message.getPayload()));
    }

    private String extractTitle(DiscordWebhookPayload payload) {
        if (payload == null || payload.embeds() == null || payload.embeds().isEmpty()) {
            return "(제목 없음)";
        }
        return payload.embeds().get(0).title();
    }
}
