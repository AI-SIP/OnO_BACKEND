package com.aisip.OnO.backend.config.rabbitmq;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리스너 컨테이너의 재시도·DLQ 정책을 고정한다.
 *
 * <p>이 설정이 빠져 있어서 실제 장애가 났다. {@code defaultRequeueRejected} 의 기본값이 true 라
 * 컨슈머가 예외를 던지면 메시지가 곧바로 큐로 돌아가 즉시 재전달됐고, 백오프도 최대 횟수도 없어
 * 큐의 TTL 이 다 될 때까지 같은 메시지를 쉬지 않고 처리했다.
 * Discord 웹훅에서는 이미 전송에 성공한 메시지가 5분 동안 재발송되며 아웃바운드 네트워크를
 * 고갈시켰고, GPT 분석은 같은 상황에서 30분 동안 OpenAI 를 다시 호출했다.
 *
 * <p>설정은 코드 몇 줄이라 조용히 사라지기 쉽다. 사라지면 같은 장애가 그대로 재현되므로
 * 여기서 동작을 고정한다. 컨슈머 단위 테스트는 "예외를 던진다"까지만 검증할 수 있고,
 * 그 예외가 몇 번 재시도되고 어디로 가는지는 이 설정에 달려 있다.
 */
@DisplayName("RabbitMQ 리스너 재시도 정책")
class RabbitListenerRetryPolicyTest extends IntegrationTestSupport {

    @Autowired
    private SimpleRabbitListenerContainerFactory listenerContainerFactory;

    @Test
    @DisplayName("재시도를 소진한 메시지는 큐로 돌려보내지 않는다 - 무한 재전달 방지")
    void rejectedMessagesAreNotRequeued() {
        SimpleMessageListenerContainer container = listenerContainerFactory.createListenerContainer();

        assertThat((Boolean) ReflectionTestUtils.getField(container, "defaultRequeueRejected"))
                .as("true 면 예외를 던진 메시지가 즉시 큐로 돌아가 무한 재전달된다. "
                        + "빠져나가는 유일한 경로가 큐 TTL 이라 그동안 외부 호출이 반복된다")
                .isFalse();
    }

    /**
     * 최대 시도 횟수는 재시도 정책에서 직접 읽는다.
     *
     * <p>가짜 호출을 만들어 실제로 돌려 보는 방식도 시도했지만, Spring AMQP 가 recoverer 를
     * 팩토리 내부 람다로 감싸 두어 호출 규약을 정확히 흉내 내기 어려웠다.
     * 값 자체를 고정하는 편이 의도도 분명하고 라이브러리 구현 변화에도 덜 흔들린다.
     */
    @Test
    @DisplayName("재시도는 세 번까지만 한다 - 영구적 실패를 빠르게 포기한다")
    void retriesAtMostThreeTimes() {
        RetryTemplate retryTemplate = (RetryTemplate) ReflectionTestUtils.getField(
                retryInterceptor(), "retryOperations");
        SimpleRetryPolicy policy = (SimpleRetryPolicy) ReflectionTestUtils.getField(
                retryTemplate, "retryPolicy");

        assertThat(policy.getMaxAttempts())
                .as("재시도가 무한하면 큐 TTL 이 다 될 때까지 외부 호출이 반복된다. "
                        + "Discord 는 5분, GPT 분석은 30분 동안 같은 요청을 다시 보냈다")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("재시도 사이에 백오프를 둔다 - 즉시 재전달로 몰아치지 않도록")
    void backsOffBetweenRetries() {
        RetryTemplate retryTemplate = (RetryTemplate) ReflectionTestUtils.getField(
                retryInterceptor(), "retryOperations");
        ExponentialBackOffPolicy backOff = (ExponentialBackOffPolicy) ReflectionTestUtils.getField(
                retryTemplate, "backOffPolicy");

        assertThat(backOff.getInitialInterval())
                .as("백오프가 없으면 세 번의 시도가 순식간에 소진돼 일시적 장애를 넘기지 못한다")
                .isPositive();
        assertThat(backOff.getMultiplier())
                .as("간격이 늘어나야 네트워크 순단이 회복될 시간을 준다")
                .isGreaterThan(1.0);
    }

    @Test
    @DisplayName("네 개 큐 모두 DLQ 이름이 선언돼 있다")
    void everyQueueDeclaresDeadLetterRouting() {
        assertThat(RabbitMQConfig.S3_DELETE_DLQ).isNotBlank();
        assertThat(RabbitMQConfig.GPT_ANALYSIS_DLQ).isNotBlank();
        assertThat(RabbitMQConfig.FCM_NOTIFICATION_DLQ).isNotBlank();
        assertThat(RabbitMQConfig.DISCORD_WEBHOOK_DLQ).isNotBlank();
    }

    private RetryOperationsInterceptor retryInterceptor() {
        SimpleMessageListenerContainer container = listenerContainerFactory.createListenerContainer();
        Advice[] adviceChain = (Advice[]) ReflectionTestUtils.getField(container, "adviceChain");

        assertThat(adviceChain)
                .as("재시도 advice 가 없으면 백오프도 최대 횟수도 걸리지 않는다")
                .isNotNull()
                .isNotEmpty();

        return (RetryOperationsInterceptor) adviceChain[0];
    }

}
