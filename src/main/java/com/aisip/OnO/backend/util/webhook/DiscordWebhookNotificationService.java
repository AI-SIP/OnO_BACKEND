package com.aisip.OnO.backend.util.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookNotificationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Instant> lastSentAtByDedupKey = new ConcurrentHashMap<>();
    private static final Duration ERROR_NOTIFICATION_DEDUP_WINDOW = Duration.ofMinutes(5);

    @Value("${discord.webhook-url}")
    private String webhookUrl;

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

        sendToDiscord(new DiscordWebhookPayload(null, List.of(embed)), dedupKey);
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

        sendToDiscord(new DiscordWebhookPayload(null, List.of(embed)));
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

        sendToDiscord(new DiscordWebhookPayload(null, List.of(embed)), dedupKey);
    }

    /**
     * Discord Webhook으로 메시지 전송 (내부 메서드)
     */
    private void sendToDiscord(DiscordWebhookPayload payload) {
        sendToDiscord(payload, null);
    }

    private void sendToDiscord(DiscordWebhookPayload payload, String dedupKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    webhookUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            if (dedupKey != null) {
                lastSentAtByDedupKey.put(dedupKey, Instant.now());
            }
            log.info("Discord webhook 전송 완료: {}", payload.embeds().get(0).title());
        } catch (Exception e) {
            log.error("Discord webhook 전송 실패: {}", e.getMessage(), e);
        }
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
