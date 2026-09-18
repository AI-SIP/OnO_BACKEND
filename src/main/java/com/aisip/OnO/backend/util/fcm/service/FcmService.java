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
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmTokenWriter fcmTokenWriter;

    private final FirebaseMessaging firebaseMessaging;
    private final MeterRegistry meterRegistry;

    private final FcmNotificationProducer fcmNotificationProducer;

    /**
     * 토큰 등록을 최대 몇 번까지 시도하는가.
     *
     * <p>중복 키든 교착이든 상대가 이미 끝났거나 되돌아간 뒤에 다시 하는 것이라 대개 두 번째에 끝난다.
     * 세 번째는 바로 그 사이에 또 다른 요청이 끼어든 경우를 위한 여유 한 번이다.
     */
    private static final int MAX_REGISTER_ATTEMPTS = 3;

    /**
     * 토큰은 기기 하나를 가리키므로 한 사용자에게만 묶여 있어야 한다.
     *
     * <p>같은 기기에서 A 가 로그아웃하고 B 가 로그인하면 같은 토큰으로 등록이 들어온다.
     * 이때 (A, 토큰) 행을 남겨 두면 A 앞으로 가는 알림(댓글 작성자 이름, 미리보기 포함)이 B 기기에 뜬다.
     * 클라이언트가 로그아웃 때 토큰을 해제하지 않으므로, 서버가 등록 시점에 이전 소유자 행을 지운다.
     * 실제 조회·삭제·삽입은 {@link FcmTokenWriter} 가 독립 트랜잭션으로 수행한다.
     *
     * <p>여기서 트랜잭션을 열지 않는 이유가 있다. 삽입은 (user_id, token) 유니크 인덱스와 경쟁하는데,
     * 실패한 트랜잭션 안에서는 재조회로 복구할 수 없다. 앱이 로그인 직후 등록과 토큰 갱신 등록을 겹쳐 보내면
     * 두 요청이 모두 "없음" 을 읽고 INSERT 해 뒤엣것이 Duplicate entry 로 떨어졌고, 운영에서 500 이 나갔다.
     * (Sentry JAVA-SPRING-BOOT-5J, JAVA-SPRING-BOOT-5H)
     *
     * <p>같은 토큰을 한 번 더 등록하는 것은 결과 상태가 같은 재시도다. 그래서 중복은 실패가 아니라 성공으로 본다.
     * 다만 롤백된 트랜잭션은 이전 소유자 행 삭제까지 되돌리므로, 새 트랜잭션에서 한 번 더 등록을 돌려 맞춘다.
     *
     * <p>경합이 중복 키로만 끝나는 것이 아니다. 같은 키를 동시에 INSERT 하면 InnoDB 가 중복 여부를 보려고
     * 상대가 쥔 레코드에 공유 잠금을 걸고 기다리는데, 그 상태에서 서로 삽입을 마치려 하면 교착이 된다.
     * 이때는 {@code Duplicate entry} 가 아니라 {@link PessimisticLockingFailureException}
     * (MySQL 교착과 잠금 대기 초과)으로 올라와 예전 코드의 중복 처리에 걸리지 않고 그대로 500 이 됐다.
     * 전체 테스트를 돌릴 때 {@code FcmTokenRegistrationConcurrencyTest} 가 INSERT 교착으로 한 번 깨진 것이 그 경로다.
     * (Sentry JAVA-SPRING-BOOT-5J 후속)
     *
     * <p>둘 다 "다시 하면 되는" 실패라 같은 방식으로 다룬다. 등록은 결과가 같은 작업이라 여러 번 돌려도 안전하다.
     * 대신 {@link #MAX_REGISTER_ATTEMPTS} 번까지만 시도한다. 무한 재시도는 DB 가 아픈 상황에서
     * 요청 스레드를 붙잡아 장애를 키운다. 그래도 안 되면 원하는 상태가 되었는지 확인하고,
     * 아니면 마지막 예외를 그대로 올려 500 과 Sentry 로 드러나게 둔다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registerToken(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_REGISTER_ATTEMPTS; attempt++) {
            try {
                fcmTokenWriter.register(fcmTokenRequestDto, userId);
                return;
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException failure) {
                lastFailure = failure;
                log.info("FCM token registration lost the race - userId: {}, attempt: {}/{}, reason: {}",
                        userId, attempt, MAX_REGISTER_ATTEMPTS, failure.getClass().getSimpleName());
            }
        }

        // 마지막 시도까지 실패했어도 다른 요청이 같은 행을 넣었으면 원하는 상태다. 그때는 성공으로 본다.
        if (fcmTokenWriter.exists(userId, fcmTokenRequestDto.token())) {
            log.info("FCM token already registered by a concurrent request - userId: {}", userId);
            return;
        }

        throw lastFailure;
    }

    public FcmTokenResponseDto findToken(String token) {
        FcmToken fcmToken = fcmTokenRepository.findByToken(token)
                        .orElseThrow(() -> new ApplicationException(FcmErrorCase.FCM_TOKEN_NOT_FOUND));

        return FcmTokenResponseDto.from(fcmToken);
    }

    public List<FcmTokenResponseDto> findUserTokens(Long userId) {
        List<FcmToken> userFcmTokenList = fcmTokenRepository.findAllByUserId(userId);
        return userFcmTokenList.stream().map(FcmTokenResponseDto::from).collect(Collectors.toList());
    }

    public void sendNotification(NotificationRequestDto dto) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Message msg = Message.builder()
                .setToken(dto.token())
                .setNotification(Notification.builder()
                        .setTitle(dto.title())
                        .setBody(dto.body())
                        .build())
                .putAllData(dto.data())
                .build();

        try {
            String messageId = firebaseMessaging.send(msg);
            recordExternalCall("firebase", "send_notification_sync", "success", sample);
            log.info("FCM 메시지 전송 성공: {}", messageId);
        } catch (FirebaseMessagingException e) {
            recordExternalCall("firebase", "send_notification_sync", "failure", sample);
            log.error("FCM 전송 실패", e);
            throw new ApplicationException(FcmErrorCase.FCM_SEND_FAILED);
        }
    }

    /**
     * 사용자의 모든 디바이스로 푸시 알림 전송 (RabbitMQ 비동기 방식)
     * - 기존 동기 방식에서 RabbitMQ 비동기 방식으로 변경
     * - Quartz Job이나 API에서 호출 시 즉시 반환
     * - 큐 적재에 실패하면 예외를 그대로 던진다. 리마인더 발송기가 이 예외로 FAILED 를 기록한다.
     * - 트랜잭션을 걸지 않는다. 트랜잭션 경계 안에서 적재 예외가 나면 호출자가 try/catch 로 삼켜도
     *   바깥 트랜잭션이 rollback-only 로 표시돼, 공유·반응·댓글 저장까지 되돌아가고 500 이 나갔다.
     */
    public void sendNotificationToAllUserDevice(Long userId, NotificationRequestDto notificationRequestDto) {
        // RabbitMQ Producer로 메시지 전송 (비동기)
        fcmNotificationProducer.sendNotificationMessage(
                userId,
                notificationRequestDto.title(),
                notificationRequestDto.body(),
                notificationRequestDto.data()
        );

        log.info("FCM 알림 메시지 큐 전송 완료 - userId: {}, title: {}", userId, notificationRequestDto.title());
    }

    private void recordExternalCall(String dependency, String operation, String outcome, Timer.Sample sample) {
        sample.stop(
                Timer.builder("ono.external.requests")
                        .description("External dependency call latency")
                        .publishPercentileHistogram()
                        .tag("dependency", dependency)
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(meterRegistry)
        );
    }

    /**
     * [DEPRECATED] 동기 방식 알림 전송 (테스트용으로만 사용)
     * @deprecated RabbitMQ 방식(sendNotificationToAllUserDevice)을 사용하세요
     */
    @Deprecated
    public void sendNotificationToAllUserDeviceSync(Long userId, NotificationRequestDto notificationRequestDto) {
        List<FcmToken> userFcmTokenList = fcmTokenRepository.findAllByUserId(userId);

        userFcmTokenList.forEach(fcmToken -> {
            try {
                sendNotification(new NotificationRequestDto(
                        fcmToken.getToken(),
                        notificationRequestDto.title(),
                        notificationRequestDto.body(),
                        notificationRequestDto.data()
                ));
            } catch (ApplicationException e) {
                log.warn("FCM 전송 실패 (userId: {}): {}", userId, e.getMessage());
            }
        });
    }
}
