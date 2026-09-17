package com.aisip.OnO.backend.config.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시도 횟수 계산만 스프링 컨텍스트 없이 확인한다.
 * 리스너 컨테이너의 실제 재시도 인터셉터를 거치는 검증은 {@code S3DeleteConsumerTest} 에 있다.
 */
@DisplayName("RabbitRetryAttempts")
class RabbitRetryAttemptsTest {

    private static final String QUEUE = RabbitMQConfig.S3_DELETE_QUEUE;

    private static Map<String, ?> death(String queue, String reason, long count) {
        return Map.of("queue", queue, "reason", reason, "count", count);
    }

    @Nested
    @DisplayName("처리 중 시도 횟수")
    class CurrentAttempt {

        @Test
        @DisplayName("재시도 인터셉터 밖에서 부르면 첫 시도로 본다")
        void firstAttemptWithoutRetryContext() {
            assertThat(RabbitRetryAttempts.currentAttempt()).isEqualTo(1);
        }

        @Test
        @DisplayName("재시도 템플릿 안에서는 실패할 때마다 1씩 올라가고, 소진 시점에는 실패 횟수를 돌려준다")
        void countsAttemptsInsideRetryTemplate() {
            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(RabbitRetryAttempts.MAX_ATTEMPTS)
                    .noBackoff()
                    .build();
            List<Integer> seen = new ArrayList<>();
            List<String> exhausted = new ArrayList<>();

            assertThatThrownBy(() -> retryTemplate.execute(
                    context -> {
                        seen.add(RabbitRetryAttempts.currentAttempt());
                        throw new IllegalStateException("fail");
                    },
                    context -> {
                        exhausted.add(RabbitRetryAttempts.exhaustedAttempts());
                        throw new IllegalStateException("exhausted");
                    }))
                    .hasMessage("exhausted");

            assertThat(seen).containsExactly(1, 2, 3);
            assertThat(exhausted).containsExactly("3/3");
            assertThat(RabbitRetryAttempts.currentAttempt())
                    .as("재시도가 끝나면 컨텍스트가 치워져 다음 메시지에 섞이지 않는다")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DLQ 의 x-death 해석")
    class DeadLetter {

        @Test
        @DisplayName("재시도 소진으로 거절된 메시지는 최대 시도 횟수만큼 시도했다")
        void rejectedOnce() {
            assertThat(RabbitRetryAttempts.describeDeadLetter(List.of(death(QUEUE, "rejected", 1)), QUEUE))
                    .isEqualTo("3회 (재시도 소진)");
        }

        @Test
        @DisplayName("DLQ 에서 되돌려 다시 실패했다면 거절된 횟수만큼 곱한다")
        void rejectedTwice() {
            assertThat(RabbitRetryAttempts.describeDeadLetter(List.of(death(QUEUE, "rejected", 2)), QUEUE))
                    .isEqualTo("6회 (재시도 소진, DLQ 이동 2번)");
        }

        @Test
        @DisplayName("큐 TTL 만료로 빠진 메시지는 시도 횟수를 지어내지 않는다")
        void expired() {
            assertThat(RabbitRetryAttempts.describeDeadLetter(List.of(death(QUEUE, "expired", 1)), QUEUE))
                    .startsWith("알 수 없음")
                    .contains("TTL");
        }

        @Test
        @DisplayName("다른 큐의 기록은 무시하고 원래 큐의 기록만 본다")
        void ignoresOtherQueues() {
            List<Map<String, ?>> xDeath = List.of(
                    death(RabbitMQConfig.S3_DELETE_DLQ, "expired", 1),
                    death(QUEUE, "rejected", 1));

            assertThat(RabbitRetryAttempts.describeDeadLetter(xDeath, QUEUE)).isEqualTo("3회 (재시도 소진)");
            assertThat(RabbitRetryAttempts.describeDeadLetter(List.of(death("other.queue", "rejected", 1)), QUEUE))
                    .startsWith("알 수 없음");
        }

        @Test
        @DisplayName("x-death 헤더가 없으면 알 수 없음으로 남긴다")
        void missingHeader() {
            assertThat(RabbitRetryAttempts.describeDeadLetter(null, QUEUE)).startsWith("알 수 없음");
            assertThat(RabbitRetryAttempts.describeDeadLetter(List.of(), QUEUE)).startsWith("알 수 없음");
        }
    }
}
