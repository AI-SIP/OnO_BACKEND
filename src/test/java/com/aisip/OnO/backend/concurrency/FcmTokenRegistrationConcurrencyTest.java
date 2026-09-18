package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import com.aisip.OnO.backend.util.fcm.service.FcmTokenWriter;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 같은 기기에서 토큰 등록 요청이 동시에 들어올 때. (Sentry JAVA-SPRING-BOOT-5J / 5H)
 *
 * <p>운영에서 {@code POST /api/fcm/token} 이 500 으로 떨어졌다.
 * {@code Duplicate entry '511-fqD5528...' for key 'fcm_token.idx_fcm_token_user_token'} 이었다.
 * 등록은 "있는지 보고 없으면 저장" 인데 (user_id, token) 에 유니크 제약이 걸려 있어,
 * 같은 사용자·같은 토큰 요청이 겹치면 둘 다 "없음" 을 읽고 INSERT 해 뒤엣것이 제약에 걸린다.
 *
 * <p>같은 토큰을 한 번 더 등록하는 것은 사용자 입장에서 아무 의미가 없는 재시도다.
 * 결과 상태(그 사용자에게 그 토큰 행이 하나)가 같으므로 둘 다 성공으로 끝나야 한다.
 *
 * <p>유니크 제약은 실제 MySQL 에서만 걸리므로 Testcontainers 컨텍스트 위에서 돌린다.
 * 통합 컨텍스트의 {@link FcmService} 는 실발송 사고를 막으려고 목이라, 여기서는 직접 만든다.
 * 다만 {@code new} 로 만든 객체는 트랜잭션 애노테이션이 적용되지 않아 검증이 헐거워지므로,
 * {@code BrokerOutageFcm} 과 같은 방식으로 트랜잭션 인터셉터를 씌운 프록시를 쓴다.
 * {@code FcmTokenWriter} 는 컨텍스트의 프록시된 실제 빈이다.
 */
@DisplayName("동시성 - FCM 토큰 등록")
class FcmTokenRegistrationConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 8;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final String DEVICE_TOKEN = "fqD5528-device-token";
    private static final int OBSERVE_COUNT = 300;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 등록 트랜잭션 경계는 여기에 있다. 프록시된 실제 빈을 써야 REQUIRES_NEW 가 걸린다. */
    @Autowired
    private FcmTokenWriter fcmTokenWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private FirebaseMessaging firebaseMessaging;
    private FcmService realFcmService;

    /**
     * FcmToken 은 테이블 기반 식별자 생성기({@code fcm_token_seq})를 쓴다.
     * FcmTokenRepositoryTest 와 같은 이유로 초기 행을 되살린다.
     */
    @BeforeEach
    void setUp() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fcm_token_seq", Integer.class);
        if (rows == null || rows == 0) {
            jdbcTemplate.update("INSERT INTO fcm_token_seq (next_val) VALUES (1)");
        }
        firebaseMessaging = mock(FirebaseMessaging.class);
        realFcmService = transactionalProxyOf(new FcmService(
                fcmTokenRepository, fcmTokenWriter, firebaseMessaging, new SimpleMeterRegistry(),
                mock(FcmNotificationProducer.class)));
    }

    /** 운영과 같게 {@code @Transactional} 을 실제로 적용한 프록시로 감싼다. */
    private FcmService transactionalProxyOf(FcmService target) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(
                new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (FcmService) proxyFactory.getProxy();
    }

    private void register(Long userId) {
        realFcmService.registerToken(new FcmTokenRequestDto(DEVICE_TOKEN), userId);
    }

    @Nested
    @DisplayName("같은 사용자가 같은 토큰을 동시에 등록")
    class SameUserSameToken {

        @RepeatedTest(3)
        @DisplayName("등록이 겹쳐 들어와도 전부 200 이고 행은 하나만 남는다")
        void keepsSingleRowWithoutServerError() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> register(USER_A));

            assertThat(outcome.serverErrors())
                    .as("유니크 제약 위반이 그대로 올라오면 토큰 등록이 500 으로 실패한다")
                    .isEmpty();
            assertThat(outcome.successCount())
                    .as("같은 결과를 만드는 중복 요청이므로 모두 성공이어야 한다")
                    .isEqualTo(THREAD_COUNT);
            assertThat(fcmTokenRepository.findAllByUserId(USER_A))
                    .extracting(FcmToken::getToken)
                    .containsExactly(DEVICE_TOKEN);
            verifyNoInteractions(firebaseMessaging);
        }

        @Test
        @DisplayName("이미 등록된 상태에서 겹쳐 들어와도 행이 늘지 않는다")
        void staysIdempotentOnAlreadyRegisteredToken() {
            register(USER_A);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> register(USER_A));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(fcmTokenRepository.findAllByUserId(USER_A)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("계정 전환과 동시 등록이 겹칠 때")
    class OwnerSwitchUnderRace {

        @RepeatedTest(3)
        @DisplayName("A 가 쓰던 기기에 B 의 등록이 겹쳐 들어와도 A 행은 사라지고 B 행만 하나 남는다")
        void removesPreviousOwnerExactlyOnce() {
            register(USER_A);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> register(USER_B));

            assertThat(outcome.serverErrors())
                    .as("계정 전환 경로에서도 중복 등록이 500 이 되면 안 된다")
                    .isEmpty();
            assertThat(fcmTokenRepository.findAllByUserId(USER_A))
                    .as("이전 소유자 행이 남으면 A 앞 알림이 B 가 쓰는 기기에 뜬다 (#271)")
                    .isEmpty();
            assertThat(fcmTokenRepository.findAllByUserId(USER_B))
                    .extracting(FcmToken::getToken)
                    .containsExactly(DEVICE_TOKEN);
            assertThat(fcmTokenRepository.findAll())
                    .as("토큰 하나는 기기 하나이고 소유자는 한 명이어야 한다")
                    .hasSize(1);
        }

        @RepeatedTest(3)
        @DisplayName("전환이 진행되는 동안 그 기기 토큰 행이 사라지는 순간은 없다")
        void neverExposesTokenlessWindow() {
            register(USER_A);
            AtomicBoolean sawNoToken = new AtomicBoolean(false);

            // 0번 스레드는 관찰자다. 이전 소유자 행 삭제와 새 행 삽입이 한 트랜잭션에 묶여 있지 않으면
            // 그 사이를 읽어 "이 기기로 갈 알림이 없는 상태" 를 잡아낸다.
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT, index -> {
                if (index == 0) {
                    for (int attempt = 0; attempt < OBSERVE_COUNT; attempt++) {
                        if (!fcmTokenRepository.existsByToken(DEVICE_TOKEN)) {
                            sawNoToken.set(true);
                            return;
                        }
                    }
                    return;
                }
                register(USER_B);
            });

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(sawNoToken.get())
                    .as("행이 잠깐이라도 사라지면 그 순간 발송 대상이 비어 알림이 유실된다")
                    .isFalse();
        }
    }
}
