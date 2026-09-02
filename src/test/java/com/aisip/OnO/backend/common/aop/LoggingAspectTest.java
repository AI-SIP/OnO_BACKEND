package com.aisip.OnO.backend.common.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 서비스 계층에서 튀어나온 예외의 로깅 정책.
 *
 * <p>ApplicationException 은 의도된 4xx 라 로그를 남기지 않고,
 * 요청 컨텍스트(traceId)가 있으면 전역 예외 핸들러가 ERROR 로 남기므로 여기서는 중복을 피한다.
 */
@DisplayName("서비스 예외 로깅 어드바이스")
class LoggingAspectTest {

    private LoggingAspect aspect;
    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        MDC.clear();
        aspect = new LoggingAspect();
        aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
        aspectLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("ApplicationException 은 의도된 흐름이라 아무것도 남기지 않는다")
    void skipsApplicationException() {
        aspect.logAfterThrowing(joinPoint(), new ApplicationException(UserErrorCase.USER_NOT_FOUND));

        assertThat(appender.list)
                .as("4xx 로 응답할 예외까지 로그로 남기면 에러 로그가 의미를 잃는다")
                .isEmpty();
    }

    @Test
    @DisplayName("요청 컨텍스트가 있으면 전역 핸들러가 남기므로 DEBUG 로만 남긴다")
    void logsDebugWhenInsideRequest() {
        MDC.put("traceId", "trace-1234");

        aspect.logAfterThrowing(joinPoint(), new IllegalStateException("boom"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage())
                .contains("UserService.findUser")
                .contains("IllegalStateException");
    }

    @Test
    @DisplayName("요청 컨텍스트 밖(배치/스케줄러)에서 터진 예외는 ERROR 로 스택과 함께 남긴다")
    void logsErrorOutsideRequest() {
        RuntimeException exception = new IllegalStateException("boom");

        aspect.logAfterThrowing(joinPoint(), exception);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("UserService.findUser");
        assertThat(event.getThrowableProxy())
                .as("배치 경로는 이 로그가 유일한 단서라 스택이 필요하다")
                .isNotNull();
    }

    private JoinPoint joinPoint() {
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("UserService.findUser(..)");
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }
}
