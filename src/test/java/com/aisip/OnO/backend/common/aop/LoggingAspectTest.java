package com.aisip.OnO.backend.common.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.util.ai.NonRetryableAnalysisException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RabbitMQ consumer 처럼 MDC traceId 가 없는 스레드에서 올라온 예외를
 * aspect 가 어떤 레벨로 남기는지 검증한다.
 * ERROR 로 남으면 Logback appender 를 타고 Sentry 로 올라간다.
 */
class LoggingAspectTest {

    private LoggingAspect aspect;
    private JoinPoint joinPoint;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aspect = new LoggingAspect();

        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("ProblemAnalysisService.analyzeProblemSync(..)");
        joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    @Test
    @DisplayName("consumer 가 처리하는 실패(HandledFailure)는 ERROR 로 남지 않는다")
    void handledFailureIsNotLoggedAsError() {
        aspect.logAfterThrowing(joinPoint, new NonRetryableAnalysisException("AI가 요청을 거절하여 분석을 진행할 수 없습니다."));

        assertThat(events()).hasSize(1);
        assertThat(events().get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events().get(0).getFormattedMessage()).contains("Handled service failure");
    }

    @Test
    @DisplayName("정말 처리되지 않은 예외는 그대로 ERROR 로 남는다")
    void unhandledExceptionIsStillLoggedAsError() {
        aspect.logAfterThrowing(joinPoint, new IllegalStateException("boom"));

        assertThat(events()).hasSize(1);
        assertThat(events().get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events().get(0).getFormattedMessage()).contains("Unhandled service exception");
    }

    @Test
    @DisplayName("ApplicationException 은 기존대로 아무것도 남기지 않는다")
    void applicationExceptionIsSkipped() {
        aspect.logAfterThrowing(joinPoint, new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("HTTP 요청 스레드(traceId 존재)에서는 기존대로 DEBUG 로만 남는다")
    void requestThreadKeepsDebugLevel() {
        MDC.put("traceId", "trace-1");

        aspect.logAfterThrowing(joinPoint, new IllegalStateException("boom"));

        assertThat(events()).hasSize(1);
        assertThat(events().get(0).getLevel()).isEqualTo(Level.DEBUG);
    }
}
