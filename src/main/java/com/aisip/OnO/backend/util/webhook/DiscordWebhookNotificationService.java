package com.aisip.OnO.backend.util.webhook;

import com.aisip.OnO.backend.config.rabbitmq.producer.DiscordWebhookProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookNotificationService {

    private final DiscordWebhookProducer discordWebhookProducer;
    private final Map<String, Instant> lastSentAtByDedupKey = new ConcurrentHashMap<>();
    private static final Duration ERROR_NOTIFICATION_DEDUP_WINDOW = Duration.ofMinutes(5);

    /**
     * 에러 발생 시 Discord로 알림 전송
     */
    public void sendErrorNotification(String path, String errorMessage, String status, String exceptionType) {
        String timestamp = Instant.now().toString();
        String dedupKey = createDedupKey(path, status, exceptionType);

        if (shouldSuppress(dedupKey)) {
            log.debug("Discord error notification suppressed - key: {}", dedupKey);
            return;
        }

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                "🚨 서버 에러 발생",
                "```" + errorMessage + "```",
                timestamp,
                List.of(
                        new DiscordWebhookPayload.Embed.Field("URL", path, false),
                        new DiscordWebhookPayload.Embed.Field("Status", status, true),
                        new DiscordWebhookPayload.Embed.Field("Time", timestamp, true),
                        new DiscordWebhookPayload.Embed.Field("Exception", exceptionType, true)
                )
        );

        publishToQueue(new DiscordWebhookPayload(null, List.of(embed)), dedupKey);
    }

    /**
     * 사용자 정의 메시지 전송
     */
    public void sendMessage(String title, String message) {
        String timestamp = Instant.now().toString();

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                title,
                message,
                timestamp,
                List.of()
        );

        publishToQueue(new DiscordWebhookPayload(null, List.of(embed)));
    }

    /**
     * 커스텀 Embed 전송
     */
    public void sendCustomEmbed(String title, String description, List<DiscordWebhookPayload.Embed.Field> fields) {
        String timestamp = Instant.now().toString();
        String dedupKey = createDedupKey(title, description);

        if (shouldSuppress(dedupKey)) {
            log.debug("Discord custom notification suppressed - key: {}", dedupKey);
            return;
        }

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                title,
                description,
                timestamp,
                fields
        );

        publishToQueue(new DiscordWebhookPayload(null, List.of(embed)), dedupKey);
    }

    /**
     * RabbitMQ를 통해 Discord Webhook 메시지를 비동기 전송합니다.
     */
    private void publishToQueue(DiscordWebhookPayload payload, String dedupKey) {
        discordWebhookProducer.send(payload, dedupKey);
        if (dedupKey != null) {
            lastSentAtByDedupKey.put(dedupKey, Instant.now());
        }
    }

    private void publishToQueue(DiscordWebhookPayload payload) {
        publishToQueue(payload, null);
    }

    private boolean shouldSuppress(String dedupKey) {
        Instant lastSentAt = lastSentAtByDedupKey.get(dedupKey);
        return lastSentAt != null
                && Instant.now().isBefore(lastSentAt.plus(ERROR_NOTIFICATION_DEDUP_WINDOW));
    }

    private String createDedupKey(String path, String status, String exceptionType) {
        return String.join("|",
                normalize(path),
                normalize(status),
                normalize(exceptionType)
        );
    }

    private String createDedupKey(String title, String description) {
        return String.join("|",
                normalize(title),
                normalize(description)
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
