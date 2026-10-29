package com.aisip.OnO.backend.common.ratelimit;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private static final String KEY_PREFIX = "rate_limit:";

    // 값이 1 이상일 때만 줄인다. GET 과 DECR 을 따로 부르면 그 사이에 키가 만료돼
    // DECR 이 TTL 없는 -1 키를 새로 만들 수 있어서 한 번에 실행한다. DECR 은 TTL 을 건드리지 않는다.
    private static final RedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('GET', KEYS[1]))\n"
                    + "if current and current > 0 then return redis.call('DECR', KEYS[1]) end\n"
                    + "return -1",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean tryConsume(String keyName, Long userId, int limitPerDay) {
        String key = KEY_PREFIX + keyName + ":" + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > limitPerDay) {
                log.warn("Rate limit exceeded - userId: {}, key: {}, count: {}/{}",
                        userId, keyName, count, limitPerDay);
                // 거절된 요청은 쓴 횟수가 아니다. 남겨 두면 막힌 요청마다 값이 한도 위로 쌓여,
                // 앞서 쓴 1회를 refund 로 돌려줘도 여전히 한도를 넘어 다시 요청할 수 없다.
                // 그 사이 키가 만료돼 TTL 없는 키가 생기지 않도록 환불과 같은 스크립트로 되돌린다.
                redisTemplate.execute(REFUND_SCRIPT, List.of(key));
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 장애 시 서비스 중단 방지를 위해 통과
            log.warn("Rate limit check failed for key: {}, failing open - reason: {}", key, e.getMessage());
            return true;
        }
    }

    /**
     * tryConsume 으로 쓴 1회를 되돌린다. 요청을 실제로 처리하지 못했을 때(예: 큐 전송 실패) 부른다.
     * 0 아래로는 내려가지 않고, 키가 이미 만료됐으면 아무것도 하지 않는다.
     * Redis 장애 시에는 tryConsume 과 같은 이유로 예외를 던지지 않고 넘어간다.
     */
    public void refund(String keyName, Long userId) {
        String key = KEY_PREFIX + keyName + ":" + userId;

        try {
            Long count = redisTemplate.execute(REFUND_SCRIPT, List.of(key));
            log.info("Rate limit refunded - userId: {}, key: {}, count: {}", userId, keyName, count);
        } catch (Exception e) {
            log.warn("Rate limit refund failed for key: {}, ignoring - reason: {}", key, e.getMessage());
        }
    }
}
