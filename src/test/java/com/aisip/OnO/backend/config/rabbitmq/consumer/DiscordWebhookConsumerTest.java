package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.message.DiscordWebhookMessage;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Discord 웹훅 발송 컨슈머 테스트.
 *
 * <p>이 컨슈머는 스프링 빈이지만 {@code RestTemplate} 을 생성자에서 직접 만들어 들고 있다.
 * 컨텍스트의 싱글턴 빈에 목 서버를 붙이면 다른 테스트에까지 영향이 가므로,
 * 여기서는 컨슈머 인스턴스를 새로 만들고 그 안의 {@code RestTemplate} 에만 목 서버를 바인딩한다.
 * 어떤 경우에도 실제 Discord 로 요청이 나가지 않는다.
 */
@DisplayName("DiscordWebhookConsumer")
class DiscordWebhookConsumerTest {

    private static final String WEBHOOK_URL = "https://discord.test/webhook/ono";

    private DiscordWebhookConsumer consumer;
    private MockRestServiceServer webhookServer;

    @BeforeEach
    void setUp() {
        consumer = new DiscordWebhookConsumer();
        ReflectionTestUtils.setField(consumer, "webhookUrl", WEBHOOK_URL);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(consumer, "restTemplate");
        webhookServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private static DiscordWebhookMessage message(List<DiscordWebhookPayload.Embed> embeds) {
        return new DiscordWebhookMessage(new DiscordWebhookPayload(null, embeds), "dedup-key");
    }

    private static DiscordWebhookPayload.Embed embed(String title) {
        return new DiscordWebhookPayload.Embed(title, "설명", "2026-09-03T00:00:00Z", List.of());
    }

    // ════════════════════════════ 정상 발송 ════════════════════════════

    @Nested
    @DisplayName("정상 발송")
    class Send {

        @Test
        @DisplayName("설정된 웹훅 URL 로 payload 를 JSON 으로 한 번 POST 한다")
        void postsPayloadOnce() {
            webhookServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.embeds[0].title").value("에러 발생"))
                    .andRespond(withSuccess());

            consumer.handleWebhookMessage(message(List.of(embed("에러 발생"))));

            webhookServer.verify();
        }
    }

    // ════════════════════════════ 실패 전파 ════════════════════════════

    @Nested
    @DisplayName("발송 실패")
    class Failure {

        @Test
        @DisplayName("웹훅이 5xx 를 내면 예외를 던져 재시도·DLQ 로 넘긴다")
        void rethrowsServerError() {
            webhookServer.expect(requestTo(WEBHOOK_URL))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> consumer.handleWebhookMessage(message(List.of(embed("에러 발생")))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Discord webhook 전송 실패");

            webhookServer.verify();
        }

        @Test
        @DisplayName("레이트 리밋(429)도 재시도 대상으로 예외를 던진다")
        void rethrowsRateLimit() {
            webhookServer.expect(requestTo(WEBHOOK_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> consumer.handleWebhookMessage(message(List.of(embed("에러 발생")))))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ════════════════════════════ payload 결손 ════════════════════════════

    @Nested
    @DisplayName("payload 결손")
    class BrokenPayload {

        @Test
        @DisplayName("embeds 가 비어 있어도 발송은 한 번으로 끝난다")
        void doesNotRetryWhenEmbedsAreEmpty() {
            webhookServer.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess());

            assertThatCode(() -> consumer.handleWebhookMessage(message(List.of())))
                    .as("전송 성공 후의 로깅이 실패로 둔갑하면 이미 나간 웹훅이 재시도로 중복 발송된다")
                    .doesNotThrowAnyException();

            webhookServer.verify();
        }

        @Test
        @DisplayName("embeds 가 null 이어도 발송 후 정상 종료한다")
        void doesNotFailWhenEmbedsAreNull() {
            webhookServer.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess());

            assertThatCode(() -> consumer.handleWebhookMessage(message(null)))
                    .doesNotThrowAnyException();

            webhookServer.verify();
        }

        @Test
        @DisplayName("payload 가 없으면 아무 요청도 보내지 않고 종료한다")
        void skipsNullPayload() {
            assertThatCode(() -> consumer.handleWebhookMessage(new DiscordWebhookMessage(null, "dedup-key")))
                    .as("보낼 본문이 없는 메시지는 재시도해도 영원히 실패한다")
                    .doesNotThrowAnyException();

            webhookServer.verify();
        }
    }

    // ════════════════════════════ DLQ ════════════════════════════

    @Nested
    @DisplayName("DLQ 핸들러")
    class DeadLetter {

        @Test
        @DisplayName("DLQ 에서는 웹훅을 다시 보내지 않는다")
        void neverResends() {
            consumer.handleWebhookDLQ(message(List.of(embed("에러 발생"))));

            webhookServer.verify();
        }

        @Test
        @DisplayName("embeds 가 비었거나 payload 가 없어도 DLQ 핸들러는 예외를 던지지 않는다")
        void survivesBrokenPayload() {
            assertThatCode(() -> consumer.handleWebhookDLQ(message(List.of())))
                    .doesNotThrowAnyException();
            assertThatCode(() -> consumer.handleWebhookDLQ(message(null)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> consumer.handleWebhookDLQ(new DiscordWebhookMessage(null, "dedup-key")))
                    .as("DLQ 에서 예외를 던지면 최종 실패 메시지마저 잃는다")
                    .doesNotThrowAnyException();

            webhookServer.verify();
        }
    }
}
