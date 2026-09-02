package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.message.DiscordWebhookMessage;
import com.aisip.OnO.backend.config.rabbitmq.message.FcmNotificationMessage;
import com.aisip.OnO.backend.config.rabbitmq.message.ProblemAnalysisMessage;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨슈머 핸들러가 받는 메시지가 실제 컨버터로 왕복되는지 확인한다.
 *
 * <p>컨슈머 자체는 메서드 직접 호출로 검증하므로, 큐를 지나며 일어나는 JSON 직렬화/역직렬화는
 * 여기서만 따로 본다. 필드가 빠진 예전 메시지가 큐에 남아 있어도 역직렬화가 깨지지 않아야
 * 컨슈머의 결손 처리 분기가 의미를 가진다.
 */
@DisplayName("RabbitMQ 메시지 직렬화")
class RabbitMessageSerializationTest extends RabbitConsumerTestSupport {

    @Autowired
    private MessageConverter messageConverter;

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T payload) {
        Message amqpMessage = messageConverter.toMessage(payload, new MessageProperties());
        return (T) messageConverter.fromMessage(amqpMessage);
    }

    @SuppressWarnings("unchecked")
    private <T> T fromJson(String json, Class<T> type) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", type.getName());
        return (T) messageConverter.fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    @Nested
    @DisplayName("왕복 변환")
    class RoundTrip {

        @Test
        @DisplayName("FCM 알림 메시지는 사용자·문구·데이터·재시도 횟수를 유지한다")
        void keepsFcmFields() {
            FcmNotificationMessage restored = roundTrip(
                    new FcmNotificationMessage(7L, "제목", "본문", Map.of("type", "review_due"), 2));

            assertThat(restored.getUserId()).isEqualTo(7L);
            assertThat(restored.getTitle()).isEqualTo("제목");
            assertThat(restored.getBody()).isEqualTo("본문");
            assertThat(restored.getData()).containsEntry("type", "review_due");
            assertThat(restored.getRetryCount()).as("재시도 횟수는 DLQ 알림에 실린다").isEqualTo(2);
        }

        @Test
        @DisplayName("S3 삭제 메시지는 URL·문제 id·재시도 횟수를 유지한다")
        void keepsS3Fields() {
            S3DeleteMessage restored = roundTrip(
                    new S3DeleteMessage("https://bucket.s3.amazonaws.com/a/b.png", 11L, 1));

            assertThat(restored.getImageUrl()).isEqualTo("https://bucket.s3.amazonaws.com/a/b.png");
            assertThat(restored.getProblemId()).isEqualTo(11L);
            assertThat(restored.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("분석 메시지는 문제 id 와 재시도 횟수를 유지한다")
        void keepsAnalysisFields() {
            ProblemAnalysisMessage restored = roundTrip(new ProblemAnalysisMessage(23L, 3));

            assertThat(restored.getProblemId()).isEqualTo(23L);
            assertThat(restored.getRetryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Discord 웹훅 메시지는 embed 본문을 유지한다")
        void keepsWebhookPayload() {
            DiscordWebhookPayload payload = new DiscordWebhookPayload(
                    null,
                    List.of(new DiscordWebhookPayload.Embed(
                            "에러 발생", "설명", "2026-09-03T00:00:00Z",
                            List.of(new DiscordWebhookPayload.Embed.Field("path", "/api/problems", true)))));

            DiscordWebhookMessage restored = roundTrip(new DiscordWebhookMessage(payload, "dedup-key"));

            assertThat(restored.getDedupKey()).isEqualTo("dedup-key");
            assertThat(restored.getPayload().embeds()).hasSize(1);
            assertThat(restored.getPayload().embeds().get(0).title()).isEqualTo("에러 발생");
            assertThat(restored.getPayload().embeds().get(0).fields().get(0).name()).isEqualTo("path");
        }
    }

    @Nested
    @DisplayName("필드가 빠진 메시지")
    class MissingFields {

        @Test
        @DisplayName("data 없는 FCM 메시지도 역직렬화된다")
        void acceptsFcmWithoutData() {
            FcmNotificationMessage restored = fromJson(
                    "{\"userId\":7,\"title\":\"제목\",\"body\":\"본문\"}", FcmNotificationMessage.class);

            assertThat(restored.getData()).isNull();
            assertThat(restored.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("빈 S3 삭제 메시지도 역직렬화된다")
        void acceptsEmptyS3Message() {
            S3DeleteMessage restored = fromJson("{}", S3DeleteMessage.class);

            assertThat(restored.getImageUrl()).isNull();
            assertThat(restored.getProblemId()).isNull();
        }

        @Test
        @DisplayName("빈 분석 메시지도 역직렬화된다")
        void acceptsEmptyAnalysisMessage() {
            ProblemAnalysisMessage restored = fromJson("{}", ProblemAnalysisMessage.class);

            assertThat(restored.getProblemId()).isNull();
            assertThat(restored.getRetryCount()).isZero();
        }
    }
}
