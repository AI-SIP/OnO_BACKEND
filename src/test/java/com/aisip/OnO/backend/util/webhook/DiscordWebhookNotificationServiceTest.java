package com.aisip.OnO.backend.util.webhook;

import com.aisip.OnO.backend.config.rabbitmq.producer.DiscordWebhookProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Discord 에러 알림 페이로드 구성과 중복 억제.
 *
 * <p>실제 웹훅 호출은 RabbitMQ 컨슈머가 하므로 여기서는 큐로 넘기는 내용만 검증한다.
 * 이 서비스는 전역 예외 핸들러 안에서 호출되므로 어떤 경우에도 예외를 밖으로 던지면 안 된다.
 */
@DisplayName("Discord 알림 서비스")
class DiscordWebhookNotificationServiceTest {

    private DiscordWebhookProducer discordWebhookProducer;
    private DiscordWebhookNotificationService notificationService;

    @BeforeEach
    void setUp() {
        discordWebhookProducer = mock(DiscordWebhookProducer.class);
        notificationService = new DiscordWebhookNotificationService(discordWebhookProducer);
    }

    private DiscordWebhookPayload capturePayload() {
        ArgumentCaptor<DiscordWebhookPayload> payload = ArgumentCaptor.forClass(DiscordWebhookPayload.class);
        verify(discordWebhookProducer).send(payload.capture(), any());
        return payload.getValue();
    }

    @Nested
    @DisplayName("에러 알림 페이로드")
    class ErrorNotificationPayload {

        @Test
        @DisplayName("경로/상태/예외타입을 필드로 담아 큐에 넣는다")
        void buildsErrorEmbed() {
            notificationService.sendErrorNotification(
                    "/api/problems", "널 포인터", "500 INTERNAL_SERVER_ERROR", "NullPointerException");

            DiscordWebhookPayload.Embed embed = capturePayload().embeds().get(0);
            assertThat(embed.title()).isEqualTo("🚨 서버 에러 발생");
            assertThat(embed.description()).contains("널 포인터");
            assertThat(embed.fields())
                    .extracting(DiscordWebhookPayload.Embed.Field::name)
                    .containsExactly("URL", "Status", "Time", "Exception");
            assertThat(embed.fields())
                    .extracting(DiscordWebhookPayload.Embed.Field::value)
                    .contains("/api/problems", "500 INTERNAL_SERVER_ERROR", "NullPointerException");
        }

        @Test
        @DisplayName("에러 메시지는 코드 블록으로 감싼다")
        void wrapsMessageInCodeBlock() {
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");

            assertThat(capturePayload().embeds().get(0).description()).isEqualTo("```boom```");
        }

        @Test
        @DisplayName("타임스탬프가 채워진다")
        void fillsTimestamp() {
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");

            assertThat(capturePayload().embeds().get(0).timestamp()).isNotBlank();
        }

        @Test
        @DisplayName("content 없이 embed 만 보낸다")
        void sendsEmbedOnly() {
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");

            DiscordWebhookPayload payload = capturePayload();
            assertThat(payload.content()).isNull();
            assertThat(payload.embeds()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("중복 억제")
    class Deduplication {

        @Test
        @DisplayName("같은 경로/상태/예외는 창 안에서 한 번만 보낸다")
        void suppressesIdenticalError() {
            for (int i = 0; i < 5; i++) {
                notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");
            }

            verify(discordWebhookProducer, times(1)).send(any(), anyString());
        }

        @Test
        @DisplayName("에러 메시지가 달라도 같은 경로/상태/예외면 억제한다 - 알림 폭주 방지")
        void dedupKeyIgnoresMessage() {
            notificationService.sendErrorNotification("/api/problems", "첫 번째", "500", "IllegalStateException");
            notificationService.sendErrorNotification("/api/problems", "두 번째", "500", "IllegalStateException");

            verify(discordWebhookProducer, times(1)).send(any(), anyString());
        }

        @Test
        @DisplayName("경로가 다르면 각각 보낸다")
        void sendsSeparatelyForDifferentPath() {
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");
            notificationService.sendErrorNotification("/api/folders", "boom", "500", "IllegalStateException");

            verify(discordWebhookProducer, times(2)).send(any(), anyString());
        }

        @Test
        @DisplayName("예외 타입이 다르면 각각 보낸다")
        void sendsSeparatelyForDifferentExceptionType() {
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "NullPointerException");

            verify(discordWebhookProducer, times(2)).send(any(), anyString());
        }

        @Test
        @DisplayName("null 값은 unknown 으로 정규화해 같은 키로 묶는다")
        void normalizesNullValues() {
            notificationService.sendErrorNotification(null, null, null, null);
            notificationService.sendErrorNotification(null, "다른 메시지", null, null);

            ArgumentCaptor<String> dedupKey = ArgumentCaptor.forClass(String.class);
            verify(discordWebhookProducer, times(1)).send(any(), dedupKey.capture());
            assertThat(dedupKey.getValue()).isEqualTo("unknown|unknown|unknown");
        }

        @Test
        @DisplayName("공백만 있는 값도 null 과 같은 unknown 키로 묶는다")
        void normalizesBlankValues() {
            notificationService.sendErrorNotification(null, "첫 번째", null, null);
            notificationService.sendErrorNotification("   ", "두 번째", "\t", "");

            ArgumentCaptor<String> dedupKey = ArgumentCaptor.forClass(String.class);
            verify(discordWebhookProducer, times(1)).send(any(), dedupKey.capture());
            assertThat(dedupKey.getValue())
                    .as("공백을 별도 키로 세면 같은 장애가 두 번 알림된다")
                    .isEqualTo("unknown|unknown|unknown");
        }

        @Test
        @DisplayName("공백이 섞인 경로는 한 칸으로 정규화해 같은 키로 본다")
        void collapsesWhitespaceInsideValues() {
            notificationService.sendErrorNotification("/api/problems  v2", "첫 번째", "500", "IllegalStateException");
            notificationService.sendErrorNotification("/api/problems v2", "두 번째", "500", "IllegalStateException");

            verify(discordWebhookProducer, times(1)).send(any(), anyString());
        }

        @Test
        @DisplayName("커스텀 embed 도 제목/설명 기준으로 중복을 억제한다")
        void suppressesIdenticalCustomEmbed() {
            notificationService.sendCustomEmbed("배치 실패", "리마인더 발송 실패", List.of());
            notificationService.sendCustomEmbed("배치 실패", "리마인더 발송 실패", List.of());

            verify(discordWebhookProducer, times(1)).send(any(), anyString());
        }

        @Test
        @DisplayName("일반 메시지는 억제 없이 매번 보낸다")
        void alwaysSendsPlainMessage() {
            notificationService.sendMessage("배포 알림", "v1.2.3 배포 완료");
            notificationService.sendMessage("배포 알림", "v1.2.3 배포 완료");

            verify(discordWebhookProducer, times(2)).send(any(), any());
        }
    }

    @Nested
    @DisplayName("커스텀 embed")
    class CustomEmbed {

        @Test
        @DisplayName("전달한 제목/설명/필드를 그대로 담는다")
        void keepsGivenFields() {
            List<DiscordWebhookPayload.Embed.Field> fields = List.of(
                    new DiscordWebhookPayload.Embed.Field("대상", "120명", true));

            notificationService.sendCustomEmbed("리마인더 발송", "발송 완료", fields);

            DiscordWebhookPayload.Embed embed = capturePayload().embeds().get(0);
            assertThat(embed.title()).isEqualTo("리마인더 발송");
            assertThat(embed.description()).isEqualTo("발송 완료");
            assertThat(embed.fields()).isEqualTo(fields);
        }
    }

    @Nested
    @DisplayName("발행 실패")
    class PublishFailure {

        @Test
        @DisplayName("큐 전송이 실패해도 예외를 밖으로 던지지 않는다")
        void swallowsProducerFailure() {
            doThrow(new IllegalStateException("rabbit down"))
                    .when(discordWebhookProducer).send(any(), any());

            assertThatCode(() -> notificationService.sendErrorNotification(
                    "/api/problems", "boom", "500", "IllegalStateException"))
                    .as("알림 실패가 원래 에러 응답을 덮으면 안 된다")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("발행에 실패했다면 다음 동일 에러는 다시 시도한다")
        void retriesAfterPublishFailure() {
            doThrow(new IllegalStateException("rabbit down"))
                    .when(discordWebhookProducer).send(any(), any());

            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");
            notificationService.sendErrorNotification("/api/problems", "boom", "500", "IllegalStateException");

            verify(discordWebhookProducer, times(2)).send(any(), anyString());
        }
    }
}
