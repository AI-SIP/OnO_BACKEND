package com.aisip.OnO.backend.util.fcm.service;

import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 등록이 교착으로 한 번 깨진 뒤 실제 MySQL 에 무엇이 남는지 본다. (Sentry JAVA-SPRING-BOOT-5J 후속)
 *
 * <p>교착은 타이밍에 달려 있어 실제로 나게 만들면 테스트가 들쭉날쭉해진다.
 * 그래서 첫 호출만 {@link CannotAcquireLockException} 을 던지고 그다음부터는 진짜 라이터로 넘기는
 * 라이터를 끼워 재시도 경로를 고정한다. 저장은 실제 트랜잭션과 유니크 제약을 그대로 탄다.
 */
@DisplayName("FCM 토큰 등록 재시도")
class FcmTokenRegisterRetryIntegrationTest extends IntegrationTestSupport {

    private static final String DEVICE_TOKEN = "retry-device-token";
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FcmTokenWriter fcmTokenWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private FirebaseMessaging firebaseMessaging;
    private AtomicInteger registerCalls;
    private FcmService fcmServiceWithFlakyWriter;

    @BeforeEach
    void setUp() {
        // FcmTokenRepositoryTest 와 같은 이유로 테이블 기반 id 생성기의 초기 행을 되살린다.
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fcm_token_seq", Integer.class);
        if (rows == null || rows == 0) {
            jdbcTemplate.update("INSERT INTO fcm_token_seq (next_val) VALUES (1)");
        }

        firebaseMessaging = mock(FirebaseMessaging.class);
        registerCalls = new AtomicInteger();
        fcmServiceWithFlakyWriter = transactionalProxyOf(new FcmService(
                fcmTokenRepository, deadlockOnFirstCall(), firebaseMessaging, new SimpleMeterRegistry(),
                mock(FcmNotificationProducer.class)));
    }

    /** 첫 등록만 교착으로 떨어뜨리고, 이후 호출은 실제 라이터에 그대로 넘긴다. */
    private FcmTokenWriter deadlockOnFirstCall() {
        FcmTokenWriter flakyWriter = mock(FcmTokenWriter.class);
        doAnswer(invocation -> {
            if (registerCalls.getAndIncrement() == 0) {
                throw new CannotAcquireLockException(
                        "Deadlock found when trying to get lock; try restarting transaction");
            }
            fcmTokenWriter.register(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(flakyWriter).register(any(), anyLong());
        when(flakyWriter.exists(anyLong(), anyString()))
                .thenAnswer(invocation -> fcmTokenWriter.exists(invocation.getArgument(0), invocation.getArgument(1)));
        return flakyWriter;
    }

    private FcmService transactionalProxyOf(FcmService target) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(
                new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (FcmService) proxyFactory.getProxy();
    }

    @Test
    @DisplayName("교착으로 한 번 실패해도 등록은 성공하고 행은 하나만 남는다")
    void registersOnceAfterDeadlock() {
        assertThatCode(() -> fcmServiceWithFlakyWriter.registerToken(new FcmTokenRequestDto(DEVICE_TOKEN), USER_A))
                .as("교착은 다시 하면 되는 실패다. 그대로 올라가면 사용자에게 500 이 나간다")
                .doesNotThrowAnyException();

        assertThat(registerCalls.get()).isEqualTo(2);
        assertThat(fcmTokenRepository.findAllByUserId(USER_A))
                .extracting(FcmToken::getToken)
                .containsExactly(DEVICE_TOKEN);
        assertThat(fcmTokenRepository.findAll()).hasSize(1);
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    @DisplayName("계정 전환이 교착으로 한 번 깨져도 이전 소유자 행이 남지 않는다")
    void keepsOwnerSwitchAfterDeadlock() {
        fcmTokenWriter.register(new FcmTokenRequestDto(DEVICE_TOKEN), USER_A);

        fcmServiceWithFlakyWriter.registerToken(new FcmTokenRequestDto(DEVICE_TOKEN), USER_B);

        assertThat(fcmTokenRepository.findAllByUserId(USER_A))
                .as("교착으로 롤백되면 이전 소유자 행 삭제도 되돌아간다. 재시도가 그것까지 다시 맞춰야 한다")
                .isEmpty();
        assertThat(fcmTokenRepository.findAllByUserId(USER_B))
                .extracting(FcmToken::getToken)
                .containsExactly(DEVICE_TOKEN);
        assertThat(fcmTokenRepository.findAll()).hasSize(1);
    }
}
