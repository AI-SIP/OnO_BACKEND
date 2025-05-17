package com.aisip.OnO.backend.common.webhook;

import java.util.List;

public record DiscordWebhookPayload(
        String content,
        List<Embed> embeds
) {
    public record Embed(
            String title,
            String description,
            String timestamp,
            List<Field> fields
    ) {
        public record Field(String name, String value, boolean inline) {}
    }
}