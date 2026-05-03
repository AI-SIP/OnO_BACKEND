package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate_limit:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(RateLimit rateLimit) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String key = KEY_PREFIX + rateLimit.key() + ":" + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (count != null && count > rateLimit.limitPerDay()) {
                log.warn("Rate limit exceeded - userId: {}, key: {}, count: {}/{}", userId, rateLimit.key(), count, rateLimit.limitPerDay());
                throw new ApplicationException(ProblemErrorCase.ANALYSIS_RATE_LIMIT_EXCEEDED);
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            // Redis 장애 시 서비스 중단 방지를 위해 통과
            log.warn("Rate limit check failed for key: {}, failing open - reason: {}", key, e.getMessage());
        }
    }
}