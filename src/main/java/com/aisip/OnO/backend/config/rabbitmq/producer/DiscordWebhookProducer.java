package com.aisip.OnO.backend.config.rabbitmq.producer;

import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.message.DiscordWebhookMessage;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(DiscordWebhookPayload payload, String dedupKey) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.DISCORD_WEBHOOK_ROUTING_KEY,
                    new DiscordWebhookMessage(payload, dedupKey)
            );
            log.debug("Discord webhook 메시지 큐 전송: {}", payload.embeds().get(0).title());
        } catch (Exception e) {
            log.error("Discord webhook 큐 전송 실패: {}", e.getMessage(), e);
        }
    }
}
