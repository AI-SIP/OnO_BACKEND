package com.aisip.OnO.backend.config.rabbitmq.message;

import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiscordWebhookMessage implements Serializable {

    private DiscordWebhookPayload payload;
    private String dedupKey;
}
