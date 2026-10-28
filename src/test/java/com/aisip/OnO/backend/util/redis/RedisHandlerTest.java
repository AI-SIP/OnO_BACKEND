package com.aisip.OnO.backend.util.redis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * executeOperation 이 실패를 삼키되 stdout 이 아니라 경고 로그로 남기는지 본다(#410).
 * 작업만 실행하므로 Redis 가 없어도 된다.
 */
@DisplayName("Redis 작업 실행기")
class RedisHandlerTest {

    private final RedisHandler redisHandler = new RedisHandler(mock(RedisConfig.class));

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RedisHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("작업이 성공하면 1 을 돌려주고 경고를 남기지 않는다")
    void returnsOneOnSuccess() {
        int[] calls = {0};

        assertThat(redisHandler.executeOperation(() -> calls[0]++)).isEqualTo(1);
        assertThat(calls[0]).isEqualTo(1);
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("작업이 실패하면 예외를 던지지 않고 0 을 돌려준다")
    void returnsZeroAndSwallowsExceptionOnFailure() {
        assertThatCode(() -> {
            int result = redisHandler.executeOperation(() -> {
                throw new RedisConnectionFailureException("connection refused");
            });
            assertThat(result).isEqualTo(0);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("작업이 실패하면 예외 메시지를 담은 경고 로그를 남긴다")
    void logsWarnWithExceptionMessageOnFailure() {
        redisHandler.executeOperation(() -> {
            throw new RedisConnectionFailureException("connection refused");
        });

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("Redis 작업 오류 발생")
                        .contains("RedisConnectionFailureException")
                        .contains("connection refused"));
    }
}
