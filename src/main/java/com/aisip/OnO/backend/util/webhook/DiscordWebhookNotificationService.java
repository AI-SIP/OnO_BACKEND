package com.aisip.OnO.backend.util.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookNotificationService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${discord.webhook-url}")
    private String webhookUrl;

    /**
     * 에러 발생 시 Discord로 알림 전송
     */
    public void sendErrorNotification(String path, String errorMessage, String status, String exceptionType) {
        String timestamp = Instant.now().toString();

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

        sendToDiscord(new DiscordWebhookPayload(null, List.of(embed)));
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

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                title,
                description,
                timestamp,
                fields
        );

        sendToDiscord(new DiscordWebhookPayload(null, List.of(embed)));
    }

    /**
     * Discord Webhook으로 메시지 전송 (내부 메서드)
     */
    private void sendToDiscord(DiscordWebhookPayload payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    webhookUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            log.info("Discord webhook 전송 완료: {}", payload.embeds().get(0).title());
        } catch (Exception e) {
            log.error("Discord webhook 전송 실패: {}", e.getMessage(), e);
        }
    }
}
