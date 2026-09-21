package com.aisip.OnO.backend.util.fcm.support;

import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import com.aisip.OnO.backend.util.fcm.service.FcmTokenWriter;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * RabbitMQ 브로커가 내려간 상황의 {@link FcmService}.
 *
 * <p>통합 테스트 컨텍스트의 {@code FcmService} 는 {@code @MockBean} 이라 트랜잭션 프록시를 거치지 않는다.
 * 그래서 "적재 실패 예외가 FcmService 의 트랜잭션 경계를 지나며 호출자 트랜잭션을 rollback-only 로
 * 만든다"는 문제가 목으로는 드러나지 않았다. 여기서는 실제 {@code FcmService} 와 실제 Producer 를 만들고,
 * 스프링이 빈에 씌우는 것과 같은 방식(애노테이션 기반 {@link TransactionInterceptor})으로 프록시를 씌운다.
 * RabbitTemplate 만 목이라 브로커나 Firebase 로 아무것도 나가지 않는다.
 *
 * <p>새 {@code @MockBean} 을 선언하면 컨텍스트가 갈라지므로, 기존 목이 이 프록시로 위임하게만 바꾼다.
 */
public final class BrokerOutageFcm {

    private final FcmService fcmService;
    private final RabbitTemplate rabbitTemplate;

    private BrokerOutageFcm(FcmService fcmService, RabbitTemplate rabbitTemplate) {
        this.fcmService = fcmService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 브로커가 계속 내려가 있다. */
    public static BrokerOutageFcm create(TransactionManager transactionManager) {
        return create(transactionManager, Integer.MAX_VALUE);
    }

    /**
     * 처음 {@code failures} 번의 적재만 실패하고 그 뒤로는 성공한다.
     * 배치에서 한 건이 실패해도 다음 건이 이어지는지 볼 때 쓴다.
     */
    public static BrokerOutageFcm create(TransactionManager transactionManager, int failures) {
        AtomicInteger attempts = new AtomicInteger();
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("convertAndSend")
                    && attempts.incrementAndGet() <= failures) {
                throw brokerDown();
            }
            return null;
        });
        FcmService target = new FcmService(
                mock(FcmTokenRepository.class),
                mock(FcmTokenWriter.class),
                mock(FirebaseMessaging.class),
                new SimpleMeterRegistry(),
                new FcmNotificationProducer(rabbitTemplate));

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return new BrokerOutageFcm((FcmService) proxyFactory.getProxy(), rabbitTemplate);
    }

    public static AmqpConnectException brokerDown() {
        return new AmqpConnectException(new ConnectException("Connection refused"));
    }

    /** 컨텍스트의 FcmService 목이 큐 적재를 이 프록시로 넘기게 한다. */
    public void routeFrom(FcmService contextMock) {
        willAnswer(invocation -> {
            fcmService.sendNotificationToAllUserDevice(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(contextMock).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
    }

    public FcmService fcmService() {
        return fcmService;
    }

    /** 성공, 실패와 상관없이 RabbitTemplate 로 적재를 시도한 횟수. */
    public long enqueueAttempts() {
        return mockingDetails(rabbitTemplate).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("convertAndSend"))
                .count();
    }
}
