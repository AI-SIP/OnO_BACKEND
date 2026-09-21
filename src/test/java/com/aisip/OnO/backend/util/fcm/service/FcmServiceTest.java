package com.aisip.OnO.backend.util.fcm.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenResponseDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.exception.FcmErrorCase;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FCM 서비스 단위 테스트.
 *
 * <p><b>여기서 실제 푸시가 나가면 실사용자 단말이 울린다.</b>
 * {@link FirebaseMessaging} 은 전부 목이며, 목 이외의 발송 경로가 생기지 않는지도 함께 검증한다.
 */
@DisplayName("FCM 서비스")
class FcmServiceTest {

    private FcmTokenRepository fcmTokenRepository;
    private FirebaseMessaging firebaseMessaging;
    private FcmNotificationProducer fcmNotificationProducer;
    private MeterRegistry meterRegistry;
    private FcmService fcmService;

    @BeforeEach
    void setUp() {
        fcmTokenRepository = mock(FcmTokenRepository.class);
        firebaseMessaging = mock(FirebaseMessaging.class);
        fcmNotificationProducer = mock(FcmNotificationProducer.class);
        meterRegistry = new SimpleMeterRegistry();
        // 등록 트랜잭션을 여는 라이터는 실제 구현을 쓴다. 프록시가 없으므로 트랜잭션은 열리지 않고
        // 저장소 호출 순서만 그대로 드러난다.
        fcmService = new FcmService(fcmTokenRepository, new FcmTokenWriter(fcmTokenRepository),
                firebaseMessaging, meterRegistry, fcmNotificationProducer);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    private FcmToken tokenOf(Long userId, String token) {
        return FcmToken.From(new FcmTokenRequestDto(token), userId);
    }

    private NotificationRequestDto notification(String token) {
        return new NotificationRequestDto(token, "복습할 시간이에요!", "복습한지 1주 지났습니다.", Map.of("type", "review"));
    }

    @Nested
    @DisplayName("토큰 등록")
    class RegisterToken {

        @Test
        @DisplayName("처음 보는 토큰은 저장한다")
        void savesNewToken() {
            when(fcmTokenRepository.existsByUserIdAndToken(1L, "token-1")).thenReturn(false);

            fcmService.registerToken(new FcmTokenRequestDto("token-1"), 1L);

            ArgumentCaptor<FcmToken> saved = ArgumentCaptor.forClass(FcmToken.class);
            verify(fcmTokenRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(1L);
            assertThat(saved.getValue().getToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("이미 등록된 토큰은 다시 저장하지 않는다 - 유니크 제약 위반 방지")
        void skipsDuplicatedToken() {
            when(fcmTokenRepository.existsByUserIdAndToken(1L, "token-1")).thenReturn(true);

            fcmService.registerToken(new FcmTokenRequestDto("token-1"), 1L);

            verify(fcmTokenRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("같은 토큰이 다른 사용자에게 묶여 있으면 그 행을 지우고 새 사용자로 저장한다 - 기기 계정 전환")
        void movesTokenFromPreviousOwner() {
            FcmToken previousOwner = tokenOf(1L, "shared-token");
            when(fcmTokenRepository.findAllByTokenAndUserIdNot("shared-token", 2L)).thenReturn(List.of(previousOwner));
            when(fcmTokenRepository.existsByUserIdAndToken(2L, "shared-token")).thenReturn(false);

            fcmService.registerToken(new FcmTokenRequestDto("shared-token"), 2L);

            verify(fcmTokenRepository).deleteAllInBatch(List.of(previousOwner));
            ArgumentCaptor<FcmToken> saved = ArgumentCaptor.forClass(FcmToken.class);
            verify(fcmTokenRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("다른 사용자에게 묶인 행이 없으면 아무것도 지우지 않는다")
        void deletesNothingWithoutPreviousOwner() {
            when(fcmTokenRepository.findAllByTokenAndUserIdNot("token-1", 1L)).thenReturn(List.of());
            when(fcmTokenRepository.existsByUserIdAndToken(1L, "token-1")).thenReturn(true);

            fcmService.registerToken(new FcmTokenRequestDto("token-1"), 1L);

            verify(fcmTokenRepository, never()).deleteAllInBatch(any());
            verify(fcmTokenRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("토큰 등록만으로는 푸시를 보내지 않는다")
        void doesNotSendOnRegistration() {
            when(fcmTokenRepository.existsByUserIdAndToken(anyLong(), anyString())).thenReturn(false);

            fcmService.registerToken(new FcmTokenRequestDto("token-1"), 1L);

            verifyNoInteractions(firebaseMessaging);
        }
    }

    @Nested
    @DisplayName("토큰 등록 재시도")
    class RegisterTokenRetry {

        private FcmTokenWriter fcmTokenWriter;
        private FcmService serviceWithMockedWriter;

        @BeforeEach
        void setUpMockedWriter() {
            fcmTokenWriter = mock(FcmTokenWriter.class);
            serviceWithMockedWriter = new FcmService(fcmTokenRepository, fcmTokenWriter,
                    firebaseMessaging, meterRegistry, fcmNotificationProducer);
        }

        private CannotAcquireLockException deadlock() {
            return new CannotAcquireLockException("Deadlock found when trying to get lock; try restarting transaction");
        }

        @Test
        @DisplayName("교착으로 한 번 실패해도 다시 시도해 성공한다 - 예전에는 그대로 500 이 나갔다")
        void retriesAfterDeadlock() {
            AtomicInteger calls = new AtomicInteger();
            doAnswer(invocation -> {
                if (calls.getAndIncrement() == 0) {
                    throw deadlock();
                }
                return null;
            }).when(fcmTokenWriter).register(any(), anyLong());

            assertThatCode(() -> serviceWithMockedWriter.registerToken(new FcmTokenRequestDto("token-1"), 1L))
                    .doesNotThrowAnyException();

            verify(fcmTokenWriter, times(2)).register(any(), anyLong());
        }

        @Test
        @DisplayName("중복 키로 한 번 실패해도 다시 시도해 성공한다")
        void retriesAfterDuplicateKey() {
            AtomicInteger calls = new AtomicInteger();
            doAnswer(invocation -> {
                if (calls.getAndIncrement() == 0) {
                    throw new DataIntegrityViolationException("Duplicate entry '1-token-1' for key 'fcm_token.idx_fcm_token_user_token'");
                }
                return null;
            }).when(fcmTokenWriter).register(any(), anyLong());

            assertThatCode(() -> serviceWithMockedWriter.registerToken(new FcmTokenRequestDto("token-1"), 1L))
                    .doesNotThrowAnyException();

            verify(fcmTokenWriter, times(2)).register(any(), anyLong());
        }

        @Test
        @DisplayName("계속 실패해도 정해진 횟수까지만 시도한다 - 무한 재시도는 요청 스레드를 붙잡는다")
        void stopsAfterMaxAttempts() {
            doThrow(deadlock()).when(fcmTokenWriter).register(any(), anyLong());
            when(fcmTokenWriter.exists(anyLong(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> serviceWithMockedWriter.registerToken(new FcmTokenRequestDto("token-1"), 1L))
                    .isInstanceOf(CannotAcquireLockException.class);

            verify(fcmTokenWriter, times(3)).register(any(), anyLong());
        }

        @Test
        @DisplayName("끝까지 실패해도 다른 요청이 같은 행을 넣었으면 성공으로 본다")
        void succeedsWhenAnotherRequestRegisteredTheSameToken() {
            doThrow(deadlock()).when(fcmTokenWriter).register(any(), anyLong());
            when(fcmTokenWriter.exists(1L, "token-1")).thenReturn(true);

            assertThatCode(() -> serviceWithMockedWriter.registerToken(new FcmTokenRequestDto("token-1"), 1L))
                    .doesNotThrowAnyException();

            verify(fcmTokenWriter, times(3)).register(any(), anyLong());
        }

        @Test
        @DisplayName("재시도해도 푸시는 보내지 않는다")
        void neverSendsWhileRetrying() {
            AtomicInteger calls = new AtomicInteger();
            doAnswer(invocation -> {
                if (calls.getAndIncrement() == 0) {
                    throw deadlock();
                }
                return null;
            }).when(fcmTokenWriter).register(any(), anyLong());

            serviceWithMockedWriter.registerToken(new FcmTokenRequestDto("token-1"), 1L);

            verifyNoInteractions(firebaseMessaging);
        }
    }

    @Nested
    @DisplayName("토큰 조회")
    class FindToken {

        @Test
        @DisplayName("등록된 토큰을 찾아 응답으로 변환한다")
        void findsRegisteredToken() {
            when(fcmTokenRepository.findByToken("token-1")).thenReturn(Optional.of(tokenOf(1L, "token-1")));

            FcmTokenResponseDto response = fcmService.findToken("token-1");

            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.token()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("없는 토큰은 404 ApplicationException 으로 거절한다")
        void throwsWhenTokenNotFound() {
            when(fcmTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fcmService.findToken("unknown"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FcmErrorCase.FCM_TOKEN_NOT_FOUND);
        }

        @Test
        @DisplayName("사용자 토큰 목록은 해당 사용자 것만 조회한다")
        void findsOnlyOwnTokens() {
            when(fcmTokenRepository.findAllByUserId(1L))
                    .thenReturn(List.of(tokenOf(1L, "phone"), tokenOf(1L, "tablet")));

            List<FcmTokenResponseDto> tokens = fcmService.findUserTokens(1L);

            assertThat(tokens).hasSize(2);
            assertThat(tokens).allSatisfy(token ->
                    assertThat(token.userId()).as("남의 토큰이 섞이면 다른 사람 기기로 알림이 간다").isEqualTo(1L));
            verify(fcmTokenRepository).findAllByUserId(1L);
        }

        @Test
        @DisplayName("등록된 기기가 없으면 빈 목록을 준다")
        void returnsEmptyListWhenNoDevice() {
            when(fcmTokenRepository.findAllByUserId(9L)).thenReturn(List.of());

            assertThat(fcmService.findUserTokens(9L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("단건 발송")
    class SendNotification {

        @Test
        @DisplayName("토큰/제목/본문/데이터를 담아 Firebase 로 넘긴다")
        void buildsMessageFromRequest() throws Exception {
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

            fcmService.sendNotification(notification("token-1"));

            verify(firebaseMessaging).send(any(Message.class));
        }

        @Test
        @DisplayName("발송 성공은 success 태그로 계측된다")
        void recordsSuccessMetric() throws Exception {
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

            fcmService.sendNotification(notification("token-1"));

            Timer timer = meterRegistry.find("ono.external.requests")
                    .tags("dependency", "firebase", "operation", "send_notification_sync", "outcome", "success")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Firebase 실패는 502 ApplicationException 으로 변환한다")
        void translatesFirebaseFailure() throws Exception {
            when(firebaseMessaging.send(any(Message.class))).thenThrow(mock(FirebaseMessagingException.class));

            assertThatThrownBy(() -> fcmService.sendNotification(notification("expired-token")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(FcmErrorCase.FCM_SEND_FAILED);
        }

        @Test
        @DisplayName("발송 실패도 failure 태그로 계측된다")
        void recordsFailureMetric() throws Exception {
            when(firebaseMessaging.send(any(Message.class))).thenThrow(mock(FirebaseMessagingException.class));

            assertThatCode(() -> fcmService.sendNotification(notification("expired-token")))
                    .isInstanceOf(ApplicationException.class);

            Timer timer = meterRegistry.find("ono.external.requests")
                    .tags("dependency", "firebase", "operation", "send_notification_sync", "outcome", "failure")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("사용자 전체 기기 발송")
    class SendToAllDevices {

        @Test
        @DisplayName("동기 발송 대신 큐에 넣는다 - 요청 스레드에서 Firebase 를 직접 부르지 않는다")
        void enqueuesInsteadOfSendingDirectly() {
            fcmService.sendNotificationToAllUserDevice(1L, notification(null));

            verify(fcmNotificationProducer).sendNotificationMessage(
                    1L, "복습할 시간이에요!", "복습한지 1주 지났습니다.", Map.of("type", "review"));
            verifyNoInteractions(firebaseMessaging);
        }

        @Test
        @DisplayName("큐 전송은 토큰 조회 없이 userId 만 넘긴다")
        void doesNotLoadTokensWhenEnqueuing() {
            fcmService.sendNotificationToAllUserDevice(1L, notification(null));

            verify(fcmTokenRepository, never()).findAllByUserId(anyLong());
        }

        @Test
        @DisplayName("큐 적재 실패는 호출자에게 그대로 알린다 - 리마인더가 FAILED 로 기록할 수 있어야 한다")
        void propagatesEnqueueFailure() {
            IllegalStateException enqueueFailure = new IllegalStateException("RabbitMQ FCM notification enqueue failed");
            doThrow(enqueueFailure).when(fcmNotificationProducer)
                    .sendNotificationMessage(anyLong(), anyString(), anyString(), any());

            assertThatThrownBy(() -> fcmService.sendNotificationToAllUserDevice(1L, notification(null)))
                    .isSameAs(enqueueFailure);
        }

        @Test
        @DisplayName("큐 적재는 트랜잭션에 참여하지 않는다 - 적재 실패가 호출자 트랜잭션을 rollback-only 로 만들면 안 된다")
        void enqueueIsNotTransactional() throws Exception {
            AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();

            assertThat(source.getTransactionAttribute(
                    FcmService.class.getMethod("sendNotificationToAllUserDevice", Long.class, NotificationRequestDto.class),
                    FcmService.class))
                    .as("트랜잭션 경계를 지나며 런타임 예외가 나면 삼켜도 바깥 트랜잭션이 커밋되지 않는다")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("동기 발송(레거시 경로)")
    class SendToAllDevicesSync {

        @Test
        @DisplayName("등록된 기기마다 한 번씩 발송한다")
        void sendsToEveryDevice() throws Exception {
            when(fcmTokenRepository.findAllByUserId(1L))
                    .thenReturn(List.of(tokenOf(1L, "phone"), tokenOf(1L, "tablet")));
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

            fcmService.sendNotificationToAllUserDeviceSync(1L, notification(null));

            verify(firebaseMessaging, times(2)).send(any(Message.class));
        }

        @Test
        @DisplayName("기기가 없으면 발송하지 않는다")
        void sendsNothingWithoutDevice() {
            when(fcmTokenRepository.findAllByUserId(1L)).thenReturn(List.of());

            fcmService.sendNotificationToAllUserDeviceSync(1L, notification(null));

            verifyNoInteractions(firebaseMessaging);
        }

        @Test
        @DisplayName("한 기기 발송이 실패해도 나머지 기기로 계속 보낸다")
        void continuesAfterSingleDeviceFailure() throws Exception {
            when(fcmTokenRepository.findAllByUserId(1L))
                    .thenReturn(List.of(tokenOf(1L, "dead-token"), tokenOf(1L, "alive-token")));
            when(firebaseMessaging.send(any(Message.class)))
                    .thenThrow(mock(FirebaseMessagingException.class))
                    .thenReturn("message-id");

            assertThatCode(() -> fcmService.sendNotificationToAllUserDeviceSync(1L, notification(null)))
                    .as("만료된 토큰 하나 때문에 나머지 기기 알림이 막히면 안 된다")
                    .doesNotThrowAnyException();

            verify(firebaseMessaging, times(2)).send(any(Message.class));
        }

        @Test
        @DisplayName("다른 사용자의 토큰은 조회 대상이 아니다")
        void loadsOnlyOwnTokens() throws Exception {
            when(fcmTokenRepository.findAllByUserId(1L)).thenReturn(List.of(tokenOf(1L, "phone")));
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

            fcmService.sendNotificationToAllUserDeviceSync(1L, notification(null));

            verify(fcmTokenRepository).findAllByUserId(1L);
            verify(fcmTokenRepository, never()).findAll();
        }
    }
}
