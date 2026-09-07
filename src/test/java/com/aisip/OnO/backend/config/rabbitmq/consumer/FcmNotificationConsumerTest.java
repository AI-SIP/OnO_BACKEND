package com.aisip.OnO.backend.config.rabbitmq.consumer;

import com.aisip.OnO.backend.config.rabbitmq.message.FcmNotificationMessage;
import com.aisip.OnO.backend.user.entity.User;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FCM 푸시 발송 컨슈머 테스트.
 *
 * <p>이 컨슈머는 실사용자에게 푸시를 쏘는 경로다. 컨텍스트에는 {@code FirebaseMessaging} 실물 빈이
 * 올라와 있으므로, 스프링이 조립한 컨슈머 빈은 쓰지 않고 <b>목 {@code FirebaseMessaging} 으로
 * 직접 조립한 인스턴스</b>를 테스트한다. FCM 토큰 조회·삭제는 실제 리포지토리와 DB 를 그대로 쓴다.
 *
 * <p>계약: 토큰이 없으면 조용히 종료(ACK), 일부 디바이스 실패는 나머지 발송을 막지 않고,
 * 전 디바이스 실패일 때만 예외를 던져 재시도·DLQ 로 넘긴다.
 */
@DisplayName("FcmNotificationConsumer")
class FcmNotificationConsumerTest extends RabbitConsumerTestSupport {

    private FirebaseMessaging firebaseMessaging;
    private SimpleMeterRegistry meterRegistry;
    private FcmNotificationConsumer consumer;

    private User owner;

    @BeforeEach
    void setUpConsumer() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        meterRegistry = new SimpleMeterRegistry();
        consumer = new FcmNotificationConsumer(
                fcmTokenRepository, firebaseMessaging, discordWebhookNotificationService, meterRegistry);
        owner = fixtures.createUser();
    }

    private static String tokenOf(Message message) {
        return (String) ReflectionTestUtils.getField(message, "token");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> dataOf(Message message) {
        return (Map<String, String>) ReflectionTestUtils.getField(message, "data");
    }

    private static String notificationField(Message message, String field) {
        Notification notification = (Notification) ReflectionTestUtils.getField(message, "notification");
        return (String) ReflectionTestUtils.getField(notification, field);
    }

    private static FirebaseMessagingException firebaseError(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(errorCode);
        return exception;
    }

    private List<Message> capturedMessages() throws FirebaseMessagingException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private double timerCount(String outcome) {
        io.micrometer.core.instrument.Timer timer = meterRegistry.find("ono.external.requests")
                .tag("dependency", "firebase")
                .tag("operation", "send_notification_async")
                .tag("outcome", outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }

    // ════════════════════════════ 정상 발송 ════════════════════════════

    @Nested
    @DisplayName("정상 발송")
    class Delivery {

        @Test
        @DisplayName("사용자에게 등록된 모든 디바이스로 발송한다")
        void sendsToEveryRegisteredDevice() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("device-a"));
            saveFcmToken(owner.getId(), uniqueToken("device-b"));

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            List<String> sentTokens = capturedMessages().stream().map(FcmNotificationConsumerTest::tokenOf).toList();
            assertThat(sentTokens)
                    .as("등록된 디바이스 수만큼 발송해야 한다")
                    .hasSize(2)
                    .allSatisfy(token -> assertThat(token).isNotBlank());
        }

        @Test
        @DisplayName("다른 사용자의 디바이스로는 발송하지 않는다")
        void neverSendsToAnotherUsersDevice() throws Exception {
            String ownerToken = uniqueToken("owner-device");
            String intruderToken = uniqueToken("intruder-device");
            User intruder = fixtures.createOtherUser();
            saveFcmToken(owner.getId(), ownerToken);
            saveFcmToken(intruder.getId(), intruderToken);

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            assertThat(capturedMessages().stream().map(FcmNotificationConsumerTest::tokenOf))
                    .as("메시지의 userId 소유 토큰으로만 나가야 한다")
                    .containsExactly(ownerToken);
        }

        @Test
        @DisplayName("제목·본문·데이터를 FCM 메시지에 그대로 담는다")
        void mapsMessageFieldsIntoFcmPayload() throws Exception {
            String token = uniqueToken("device");
            saveFcmToken(owner.getId(), token);
            Map<String, String> data = Map.of("type", "review_due", "problemId", "42");

            consumer.handleNotificationMessage(
                    new FcmNotificationMessage(owner.getId(), "복습 알림", "3문제가 기다려요", data));

            Message sent = capturedMessages().get(0);
            assertThat(tokenOf(sent)).isEqualTo(token);
            assertThat(notificationField(sent, "title")).isEqualTo("복습 알림");
            assertThat(notificationField(sent, "body")).isEqualTo("3문제가 기다려요");
            assertThat(dataOf(sent)).containsExactlyInAnyOrderEntriesOf(data);
        }

        @Test
        @DisplayName("발송 성공은 외부 호출 메트릭에 success 로 기록된다")
        void recordsSuccessMetric() {
            saveFcmToken(owner.getId(), uniqueToken("device-a"));
            saveFcmToken(owner.getId(), uniqueToken("device-b"));

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            assertThat(timerCount("success")).as("디바이스마다 한 번씩 기록된다").isEqualTo(2);
            assertThat(timerCount("failure")).isZero();
        }

        @Test
        @DisplayName("정상 발송에서는 DLQ 알림을 보내지 않는다")
        void doesNotNotifyDiscordOnSuccess() {
            saveFcmToken(owner.getId(), uniqueToken("device"));

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            verifyNoInteractions(discordWebhookNotificationService);
        }
    }

    // ════════════════════════════ 건너뛰기 ════════════════════════════

    @Nested
    @DisplayName("발송 대상이 없을 때")
    class SkipWithoutToken {

        @Test
        @DisplayName("토큰이 하나도 없으면 예외 없이 조용히 끝난다")
        void skipsSilentlyWhenNoToken() {
            assertThatCode(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId())))
                    .as("토큰 미등록은 정상 처리다. 예외를 던지면 무의미한 재시도가 반복된다")
                    .doesNotThrowAnyException();

            verifyNoInteractions(firebaseMessaging);
            assertThat(timerCount("success")).isZero();
            assertThat(timerCount("failure")).isZero();
        }

        @Test
        @DisplayName("userId 가 null 이어도 NPE 없이 건너뛴다")
        void skipsWhenUserIdIsNull() {
            saveFcmToken(owner.getId(), uniqueToken("device"));

            assertThatCode(() -> consumer.handleNotificationMessage(fcmMessage(null)))
                    .doesNotThrowAnyException();

            verifyNoInteractions(firebaseMessaging);
        }
    }

    // ════════════════════════════ 부분 실패 ════════════════════════════

    @Nested
    @DisplayName("일부 디바이스 실패")
    class PartialFailure {

        @Test
        @DisplayName("한 디바이스가 실패해도 나머지 디바이스로 계속 보낸다")
        void continuesAfterOneDeviceFails() throws Exception {
            String failing = uniqueToken("failing-device");
            String healthy = uniqueToken("healthy-device");
            saveFcmToken(owner.getId(), failing);
            saveFcmToken(owner.getId(), healthy);
            willThrow(firebaseError(MessagingErrorCode.INTERNAL))
                    .given(firebaseMessaging).send(argThat(m -> failing.equals(tokenOf(m))));

            assertThatCode(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId())))
                    .as("한 대라도 성공했으면 재시도가 필요 없다")
                    .doesNotThrowAnyException();

            assertThat(capturedMessages().stream().map(FcmNotificationConsumerTest::tokenOf))
                    .containsExactlyInAnyOrder(failing, healthy);
            assertThat(timerCount("success")).isEqualTo(1);
            assertThat(timerCount("failure")).isEqualTo(1);
        }

        @Test
        @DisplayName("UNREGISTERED 토큰은 삭제하고 나머지 토큰은 남긴다")
        void deletesUnregisteredToken() throws Exception {
            String expired = uniqueToken("expired-device");
            String healthy = uniqueToken("healthy-device");
            saveFcmToken(owner.getId(), expired);
            saveFcmToken(owner.getId(), healthy);
            willThrow(firebaseError(MessagingErrorCode.UNREGISTERED))
                    .given(firebaseMessaging).send(argThat(m -> expired.equals(tokenOf(m))));

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            assertThat(fcmTokenRepository.findByToken(expired))
                    .as("무효 토큰은 정리되어야 다음 발송에서 다시 실패하지 않는다")
                    .isEmpty();
            assertThat(fcmTokenRepository.findByToken(healthy)).isPresent();
        }

        @Test
        @DisplayName("INVALID_ARGUMENT 토큰도 삭제한다")
        void deletesInvalidArgumentToken() throws Exception {
            String invalid = uniqueToken("invalid-device");
            saveFcmToken(owner.getId(), invalid);
            willThrow(firebaseError(MessagingErrorCode.INVALID_ARGUMENT))
                    .given(firebaseMessaging).send(any(Message.class));

            consumer.handleNotificationMessage(fcmMessage(owner.getId()));

            assertThat(fcmTokenRepository.findByToken(invalid)).isEmpty();
        }

        @Test
        @DisplayName("무효 토큰만 있었다면 정리만 하고 재시도를 요청하지 않는다")
        void doesNotRetryWhenOnlyInvalidTokensExisted() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("expired-device"));
            willThrow(firebaseError(MessagingErrorCode.UNREGISTERED))
                    .given(firebaseMessaging).send(any(Message.class));

            assertThatCode(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId())))
                    .as("보낼 곳이 사라진 것은 재시도로 해결되지 않는다")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("일시 오류로 모든 디바이스가 실패하면 예외를 던져 재시도·DLQ 로 넘긴다")
        void throwsWhenEveryDeviceFails() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("device-a"));
            saveFcmToken(owner.getId(), uniqueToken("device-b"));
            willThrow(firebaseError(MessagingErrorCode.INTERNAL))
                    .given(firebaseMessaging).send(any(Message.class));

            assertThatThrownBy(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(String.valueOf(owner.getId()));

            assertThat(timerCount("failure")).isEqualTo(2);
        }

        @Test
        @DisplayName("FCM 이 아닌 예외로 모든 발송이 실패해도 예외를 전파한다")
        void throwsOnUnexpectedError() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("device"));
            willThrow(new IllegalStateException("firebase down"))
                    .given(firebaseMessaging).send(any(Message.class));

            assertThatThrownBy(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("FCM 푸시 알림 전송 실패");
        }
    }

    // ════════════════════════════ 메시지 결손 ════════════════════════════

    @Nested
    @DisplayName("메시지 필드 결손")
    class MissingFields {

        @Test
        @DisplayName("data 가 null 이어도 알림 자체는 발송한다")
        void sendsNotificationWhenDataIsNull() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("device"));

            assertThatCode(() -> consumer.handleNotificationMessage(fcmMessage(owner.getId(), null)))
                    .as("data 없는 메시지가 NPE 로 전 디바이스 실패 처리되면 안 된다")
                    .doesNotThrowAnyException();

            Message sent = capturedMessages().get(0);
            assertThat(dataOf(sent)).as("데이터 없이 알림만 나간다").isNull();
            assertThat(timerCount("success")).isEqualTo(1);
        }

        @Test
        @DisplayName("data 가 비어 있어도 발송한다")
        void sendsNotificationWhenDataIsEmpty() throws Exception {
            saveFcmToken(owner.getId(), uniqueToken("device"));

            consumer.handleNotificationMessage(fcmMessage(owner.getId(), new HashMap<>()));

            assertThat(capturedMessages()).hasSize(1);
        }
    }

    // ════════════════════════════ DLQ ════════════════════════════

    @Nested
    @DisplayName("DLQ 핸들러")
    class DeadLetter {

        @Test
        @DisplayName("최종 실패 메시지는 Discord 로 알린다")
        void notifiesDiscord() {
            consumer.handleNotificationDLQ(fcmMessageWithRetryCount(owner.getId(), 3));

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(discordWebhookNotificationService).sendErrorNotification(
                    eq("RabbitMQ DLQ - FCM Notification"),
                    details.capture(),
                    eq("ERROR"),
                    anyString());

            assertThat(details.getValue())
                    .as("어느 사용자의 몇 번째 재시도가 최종 실패했는지 알 수 있어야 한다")
                    .contains(String.valueOf(owner.getId()))
                    .contains("3");
        }

        @Test
        @DisplayName("DLQ 처리에서 푸시를 다시 보내지는 않는다")
        void doesNotResendPush() {
            saveFcmToken(owner.getId(), uniqueToken("device"));

            consumer.handleNotificationDLQ(fcmMessage(owner.getId()));

            verifyNoInteractions(firebaseMessaging);
        }

        @Test
        @DisplayName("Discord 알림이 실패해도 DLQ 핸들러는 예외를 던지지 않는다")
        void swallowsDiscordFailure() {
            willThrow(new IllegalStateException("webhook down"))
                    .given(discordWebhookNotificationService)
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());

            assertThatCode(() -> consumer.handleNotificationDLQ(fcmMessage(owner.getId())))
                    .as("DLQ 에서 예외를 던지면 최종 실패 메시지마저 잃는다")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("userId 가 null 인 메시지도 DLQ 알림을 만들 수 있다")
        void handlesNullUserId() {
            assertThatCode(() -> consumer.handleNotificationDLQ(fcmMessage(null)))
                    .doesNotThrowAnyException();

            verify(discordWebhookNotificationService, times(1))
                    .sendErrorNotification(anyString(), anyString(), anyString(), anyString());
        }
    }
}
