package com.aisip.OnO.backend.util.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 명령과 연결 타임아웃이 Lettuce 기본값(60초)이 아니라 설정값으로 들어가는지 본다(#389).
 * 팩토리를 시작하지 않고 설정만 확인하므로 Redis 가 없어도 된다.
 */
@DisplayName("Redis 타임아웃 설정")
class RedisConfigTimeoutTest {

    private LettuceConnectionFactory createFactory(long commandTimeoutMs, long connectTimeoutMs) {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "commandTimeoutMs", commandTimeoutMs);
        ReflectionTestUtils.setField(config, "connectTimeoutMs", connectTimeoutMs);
        return (LettuceConnectionFactory) config.redisConnectionFactory();
    }

    @Test
    @DisplayName("명령 타임아웃과 연결 타임아웃이 설정값으로 들어간다")
    void appliesConfiguredTimeouts() {
        LettuceConnectionFactory factory = createFactory(2000, 1500);
        LettuceClientConfiguration clientConfiguration = factory.getClientConfiguration();

        assertThat(clientConfiguration.getCommandTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(clientConfiguration.getClientOptions()).isPresent();
        assertThat(clientConfiguration.getClientOptions().get().getSocketOptions().getConnectTimeout())
                .isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    @DisplayName("Lettuce 기본 명령 타임아웃 60초를 쓰지 않는다")
    void doesNotUseLettuceDefaultTimeout() {
        LettuceConnectionFactory factory = createFactory(2000, 2000);

        assertThat(factory.getClientConfiguration().getCommandTimeout()).isLessThan(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("접속 대상 호스트와 포트는 그대로다")
    void keepsHostAndPort() {
        LettuceConnectionFactory factory = createFactory(2000, 2000);

        assertThat(factory.getHostName()).isEqualTo("localhost");
        assertThat(factory.getPort()).isEqualTo(6379);
    }
}
