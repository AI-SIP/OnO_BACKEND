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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 같은 기기에서 계정이 바뀔 때 토큰 행이 실제 MySQL 에서 어떻게 남는지 본다. (#271)
 *
 * <p>통합 컨텍스트의 {@link FcmService} 는 발송 사고를 막으려고 목으로 바꿔 두었다.
 * 그래서 저장소만 실제 빈을 쓰고 서비스는 여기서 직접 만든다. 발송 의존성은 전부 목이다.
 * {@code new} 로 만든 객체에는 트랜잭션 애노테이션이 걸리지 않으므로 트랜잭션 인터셉터를 씌운다.
 */
@DisplayName("FCM 토큰 계정 전환")
class FcmTokenOwnerSwitchIntegrationTest extends IntegrationTestSupport {

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

    @BeforeEach
    void setUp() {
        // FcmTokenRepositoryTest 와 같은 이유로 테이블 기반 id 생성기의 초기 행을 되살린다.
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fcm_token_seq", Integer.class);
        if (rows == null || rows == 0) {
            jdbcTemplate.update("INSERT INTO fcm_token_seq (next_val) VALUES (1)");
        }
        firebaseMessaging = mock(FirebaseMessaging.class);
        FcmService target = new FcmService(
                fcmTokenRepository, fcmTokenWriter, firebaseMessaging, new SimpleMeterRegistry(),
                mock(FcmNotificationProducer.class));
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(
                new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        realFcmService = (FcmService) proxyFactory.getProxy();
    }

    @Test
    @DisplayName("A 가 쓰던 기기에 B 가 로그인해 등록하면 A 의 토큰 행이 사라진다")
    void removesPreviousOwnerOnAccountSwitch() {
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);

        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 2L);

        assertThat(fcmTokenRepository.findAllByUserId(1L))
                .as("A 행이 남으면 A 앞 알림이 B 가 쓰는 기기에 뜬다")
                .isEmpty();
        assertThat(fcmTokenRepository.findAllByUserId(2L))
                .extracting(FcmToken::getToken)
                .containsExactly("device-token");
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    @DisplayName("A 가 다시 로그인하면 B 행이 지워지고 A 로 돌아온다")
    void switchesBackToOriginalOwner() {
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 2L);

        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);

        assertThat(fcmTokenRepository.findAllByUserId(2L)).isEmpty();
        assertThat(fcmTokenRepository.findAllByUserId(1L))
                .extracting(FcmToken::getToken)
                .containsExactly("device-token");
    }

    @Test
    @DisplayName("같은 사용자가 같은 토큰을 여러 번 등록해도 행은 하나다")
    void sameUserRegistrationIsIdempotent() {
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);

        assertThat(fcmTokenRepository.findAllByUserId(1L)).hasSize(1);
    }

    @Test
    @DisplayName("같은 사용자의 다른 기기 토큰과 다른 사용자의 다른 토큰은 건드리지 않는다")
    void keepsUnrelatedTokens() {
        realFcmService.registerToken(new FcmTokenRequestDto("my-tablet"), 2L);
        realFcmService.registerToken(new FcmTokenRequestDto("other-phone"), 3L);
        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 1L);

        realFcmService.registerToken(new FcmTokenRequestDto("device-token"), 2L);

        assertThat(fcmTokenRepository.findAllByUserId(2L))
                .extracting(FcmToken::getToken)
                .containsExactlyInAnyOrder("my-tablet", "device-token");
        assertThat(fcmTokenRepository.findAllByUserId(3L))
                .extracting(FcmToken::getToken)
                .containsExactly("other-phone");
    }
}
