package com.aisip.OnO.backend.config.rabbitmq.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aisip.OnO.backend.config.rabbitmq.RabbitMQConfig;
import com.aisip.OnO.backend.config.rabbitmq.RabbitRetryAttempts;
import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import com.rabbitmq.client.Channel;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * S3 이미지 삭제 컨슈머 테스트.
 *
 * <p>{@code FileUploadService} 는 베이스에서 목으로 잡혀 있어 실제 S3 객체가 지워지지 않는다.
 * 삭제는 되돌릴 수 없으므로 이 테스트는 <b>어떤 URL 로 삭제가 요청되는지</b>를 검증하는 데 집중한다.
 *
 * <p>계약: 삭제 실패는 예외로 전파해 재시도·DLQ 를 태우고, DLQ 알림에는 S3 객체 키를 마스킹해 남긴다.
 */
@DisplayName("S3DeleteConsumer")
class S3DeleteConsumerTest extends RabbitConsumerTestSupport {

    private static final String IMAGE_URL =
            "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/problem/2026/ab12cd34-ef56-7890-abcd-ef1234567890.png";

    @Autowired
    private S3DeleteConsumer consumer;

    private String capturedDlqDetails() {
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(discordWebhookNotificationService).sendErrorNotification(
                eq("RabbitMQ DLQ - S3 Delete"), details.capture(), eq("ERROR"), anyString());
        return details.getValue();
    }

    // ════════════════════════════ 정상 처리 ════════════════════════════

    @Nested
    @DisplayName("정상 처리")
    class Delete {

        @Test
        @DisplayName("메시지의 이미지 URL 을 그대로 S3 삭제에 넘긴다")
        void deletesRequestedImage() {
            consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 10L));

            verify(fileUploadService).deleteImageFileFromS3(IMAGE_URL);
            verifyNoInteractions(discordWebhookNotificationService);
        }

        @Test
        @DisplayName("삭제 대상이 아닌 다른 URL 로는 삭제를 요청하지 않는다")
        void deletesOnlyTheGivenUrl() {
            consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 10L));

            verify(fileUploadService, never()).deleteImageFileFromS3(
                    "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/other.png");
        }
    }

    // ════════════════════════════ 실패 전파 ════════════════════════════

    @Nested
    @DisplayName("삭제 실패")
    class Failure {

        @Test
        @DisplayName("S3 삭제가 실패하면 예외를 던져 재시도·DLQ 로 넘긴다")
        void propagatesFailure() {
            willThrow(new IllegalStateException("S3 timeout"))
                    .given(fileUploadService).deleteImageFileFromS3(IMAGE_URL);

            assertThatThrownBy(() -> consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 77L)))
                    .as("예외를 삼키면 지워지지 않은 이미지가 조용히 남는다")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("77")
                    .hasRootCauseMessage("S3 timeout");
        }

        @Test
        @DisplayName("실패 시점에는 Discord 알림을 보내지 않는다 (재시도 여지가 남아 있다)")
        void doesNotNotifyOnFirstFailure() {
            willThrow(new IllegalStateException("S3 timeout"))
                    .given(fileUploadService).deleteImageFileFromS3(IMAGE_URL);

            assertThatThrownBy(() -> consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 77L)))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    // ════════════════════════════ 메시지 결손 ════════════════════════════

    @Nested
    @DisplayName("메시지 필드 결손")
    class MissingFields {

        @Test
        @DisplayName("imageUrl 이 null 이면 삭제를 시도하지 않고 종료한다")
        void skipsNullImageUrl() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message(null, 10L)))
                    .as("삭제할 대상이 없는 메시지는 재시도해도 영원히 실패한다")
                    .doesNotThrowAnyException();

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("imageUrl 이 빈 문자열이어도 삭제를 시도하지 않는다")
        void skipsBlankImageUrl() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message("   ", 10L)))
                    .doesNotThrowAnyException();

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("problemId 가 null 이어도 삭제는 정상 수행된다")
        void deletesEvenWithoutProblemId() {
            assertThatCode(() -> consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, null)))
                    .doesNotThrowAnyException();

            verify(fileUploadService).deleteImageFileFromS3(IMAGE_URL);
        }
    }

    // ════════════════════════════ DLQ ════════════════════════════

    @Nested
    @DisplayName("DLQ 핸들러")
    class DeadLetter {

        @Test
        @DisplayName("최종 실패 메시지는 Discord 로 알린다")
        void notifiesDiscord() {
            consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE));

            assertThat(capturedDlqDetails())
                    .as("어떤 문제가 몇 번 시도한 끝에 최종 실패했는지 알 수 있어야 한다")
                    .contains("**Problem ID:** 77")
                    .contains("**Attempts:** 3회");
        }

        @Test
        @DisplayName("DLQ 알림에 S3 객체 키 원문을 그대로 넣지 않는다")
        void masksObjectKey() {
            consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE));

            String details = capturedDlqDetails();
            assertThat(details)
                    .as("객체 키 원문이 외부 채널로 그대로 나가면 안 된다")
                    .doesNotContain("ab12cd34-ef56-7890-abcd-ef1234567890.png")
                    .doesNotContain(IMAGE_URL);
            assertThat(details).as("마스킹 흔적은 남아야 추적이 가능하다").contains("***");
        }

        @Test
        @DisplayName("짧은 객체 키는 통째로 가린다")
        void masksShortObjectKeyCompletely() {
            consumer.handleS3DeleteDLQ(
                    s3Message("https://test-ono-bucket.s3.amazonaws.com/a.png", 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE));

            assertThat(capturedDlqDetails())
                    .as("앞뒤를 남기면 짧은 키는 사실상 노출된다")
                    .doesNotContain("a.png")
                    .contains("***");
        }

        @Test
        @DisplayName("imageUrl 이 null 이면 unknown 으로 알린다")
        void reportsUnknownForNullUrl() {
            assertThatCode(() -> consumer.handleS3DeleteDLQ(s3Message(null, 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE)))
                    .doesNotThrowAnyException();

            assertThat(capturedDlqDetails()).contains("unknown");
        }

        @Test
        @DisplayName("DLQ 처리에서 S3 삭제를 다시 시도하지는 않는다")
        void doesNotRetryDeletion() {
            consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE));

            verifyNoInteractions(fileUploadService);
        }

        @Test
        @DisplayName("Discord 알림이 실패해도 DLQ 핸들러는 예외를 던지지 않는다")
        void swallowsDiscordFailure() {
            willThrow(new IllegalStateException("webhook down"))
                    .given(discordWebhookNotificationService)
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());

            assertThatCode(() -> consumer.handleS3DeleteDLQ(s3Message(IMAGE_URL, 77L), rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE)))
                    .as("DLQ 에서 예외를 던지면 최종 실패 기록마저 잃는다")
                    .doesNotThrowAnyException();
        }
    }

    // ════════════════════════════ 실제 재시도·DLQ 경로 ════════════════════════════

    /**
     * 컨슈머를 리스너 컨테이너가 실제로 쓰는 재시도 인터셉터와 리스너 어댑터로 감싸 돌린다.
     * 위의 테스트들은 핸들러를 직접 부르므로 시도 횟수가 항상 첫 시도로 잡힌다.
     */
    @Nested
    @DisplayName("실제 재시도·DLQ 경로의 시도 횟수")
    class Attempts {

        @Autowired
        private SimpleRabbitListenerContainerFactory listenerContainerFactory;

        @Autowired
        private RabbitListenerEndpointRegistry listenerEndpointRegistry;

        @Autowired
        private MessageConverter messageConverter;

        /** 재시도 인터셉터는 (channel, message) 인자를 받는 리스너 호출을 감싼다. */
        interface ListenerInvoker {
            void invoke(Channel channel, Message message);
        }

        @Test
        @DisplayName("세 번 실패하면 로그에 1/3, 2/3, 3/3 이 찍히고 거절 사유에 실패 횟수가 남는다")
        void logsEachAttemptThroughRetryInterceptor() {
            willThrow(new IllegalStateException("S3 timeout"))
                    .given(fileUploadService).deleteImageFileFromS3(IMAGE_URL);

            SimpleMessageListenerContainer container = listenerContainerFactory.createListenerContainer();
            Advice[] adviceChain = (Advice[]) ReflectionTestUtils.getField(container, "adviceChain");

            ProxyFactory proxyFactory = new ProxyFactory();
            proxyFactory.addInterface(ListenerInvoker.class);
            proxyFactory.setTarget((ListenerInvoker) (channel, message) ->
                    consumer.handleS3DeleteMessage(s3Message(IMAGE_URL, 77L)));
            Arrays.stream(adviceChain).forEach(proxyFactory::addAdvice);
            ListenerInvoker invoker = (ListenerInvoker) proxyFactory.getProxy();

            Logger logger = (Logger) LoggerFactory.getLogger(S3DeleteConsumer.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                Message amqpMessage = messageConverter.toMessage(s3Message(IMAGE_URL, 77L), new MessageProperties());

                assertThatThrownBy(() -> invoker.invoke(mock(Channel.class), amqpMessage))
                        .as("재시도를 소진하면 거절되어 DLQ 로 간다. 거절 사유에 실제 실패 횟수가 남는다")
                        .hasMessageContaining("attempts: 3/3");
            } finally {
                logger.detachAppender(appender);
            }

            List<String> failures = appender.list.stream()
                    .filter(event -> event.getMessage().startsWith("RabbitMQ message failed"))
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(failures).hasSize(RabbitRetryAttempts.MAX_ATTEMPTS);
            assertThat(failures.get(0)).contains("attempt: 1/3");
            assertThat(failures.get(1)).contains("attempt: 2/3");
            assertThat(failures.get(2)).contains("attempt: 3/3");
        }

        @Test
        @DisplayName("DLQ 리스너는 x-death 헤더를 받아 시도 횟수를 알린다")
        void dlqListenerReadsXDeathHeader() throws Exception {
            deliverToDlq(Map.of(RabbitRetryAttempts.X_DEATH_HEADER, rejectedAfterRetries(RabbitMQConfig.S3_DELETE_QUEUE)));

            assertThat(capturedDlqDetails()).contains("**Attempts:** 3회 (재시도 소진)");
        }

        @Test
        @DisplayName("x-death 헤더가 없어도 DLQ 리스너는 알림을 보낸다")
        void dlqListenerWithoutXDeathHeader() throws Exception {
            deliverToDlq(Map.of());

            assertThat(capturedDlqDetails()).contains("**Attempts:** 알 수 없음");
        }

        private void deliverToDlq(Map<String, Object> headers) throws Exception {
            MessageProperties properties = new MessageProperties();
            headers.forEach(properties::setHeader);
            Message amqpMessage = messageConverter.toMessage(new S3DeleteMessage(IMAGE_URL, 77L), properties);

            AbstractMessageListenerContainer dlqContainer = listenerEndpointRegistry.getListenerContainers().stream()
                    .map(AbstractMessageListenerContainer.class::cast)
                    .filter(container -> Arrays.asList(container.getQueueNames()).contains(RabbitMQConfig.S3_DELETE_DLQ))
                    .findFirst()
                    .orElseThrow();

            ((ChannelAwareMessageListener) dlqContainer.getMessageListener())
                    .onMessage(amqpMessage, mock(Channel.class));
        }
    }
}
