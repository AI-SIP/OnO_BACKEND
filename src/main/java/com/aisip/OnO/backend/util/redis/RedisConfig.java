package com.aisip.OnO.backend.util.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    /**
     * 명령 하나를 기다리는 최대 시간. 팩토리를 직접 만들어서 spring.data.redis.timeout 이 적용되지 않고,
     * 지정하지 않으면 Lettuce 기본값 60초가 쓰인다. 한도 확인처럼 요청 트랜잭션 안에서 부르는 곳이 있어
     * Redis 가 응답하지 않으면 DB 커넥션을 60초씩 잡고 있게 되므로 짧게 둔다(#389).
     */
    @Value("${ono.redis.command-timeout-ms:2000}")
    private long commandTimeoutMs;

    @Value("${ono.redis.connect-timeout-ms:2000}")
    private long connectTimeoutMs;

    /**
     * Redis 연결을 위한 'Connection' 생성합니다.
     *
     * @return RedisConnectionFactory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(commandTimeoutMs))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                                .build())
                        .build())
                .build();
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
    }

    /**
     * Redis 데이터 처리를 위한 템플릿을 구성합니다.
     * 해당 구성된 RedisTemplate을 통해서 데이터 통신으로 처리되는 대한 직렬화를 수행합니다.
     *
     * @return RedisTemplate<String, Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        // Redis를 연결합니다.
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        // Key-Value 형태로 직렬화를 수행합니다.
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        // Hash Key-Value 형태로 직렬화를 수행합니다.
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        // 기본적으로 직렬화를 수행합니다.
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());

        return redisTemplate;
    }
}
