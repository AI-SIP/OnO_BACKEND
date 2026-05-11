package com.aisip.OnO.backend.common.ratelimit;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private static final String KEY_PREFIX = "rate_limit:";

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
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 장애 시 서비스 중단 방지를 위해 통과
            log.warn("Rate limit check failed for key: {}, failing open - reason: {}", key, e.getMessage());
            return true;
        }
    }
}
