package com.aisip.OnO.backend.common.ratelimit;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 컨테이너 위에서 검증하는 사용자별 일일 요청 한도.
 *
 * <p>카운터가 사용자/키별로 분리되지 않으면 한 사용자가 다른 사용자의 한도를 소진시킬 수 있다.
 * 키 이름은 테스트마다 고유하게 만들어 실행 순서에 영향을 받지 않게 한다.
 */
@DisplayName("요청 한도 서비스")
class RateLimitServiceTest extends IntegrationTestSupport {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String uniqueKey() {
        return "test_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String redisKey(String keyName, Long userId) {
        return "rate_limit:" + keyName + ":" + userId;
    }

    @Nested
    @DisplayName("한도 소비")
    class Consume {

        @Test
        @DisplayName("한도 안에서는 계속 통과한다")
        void passesWithinLimit() {
            String key = uniqueKey();

            for (int i = 1; i <= 3; i++) {
                assertThat(rateLimitService.tryConsume(key, 1L, 3))
                        .as("%d번째 요청은 한도(3) 안이다", i)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("한도를 넘긴 요청부터 차단하고 이후 요청도 계속 막는다")
        void blocksAfterLimitExceeded() {
            String key = uniqueKey();
            for (int i = 0; i < 3; i++) {
                rateLimitService.tryConsume(key, 1L, 3);
            }

            assertThat(rateLimitService.tryConsume(key, 1L, 3)).as("4번째 요청").isFalse();
            assertThat(rateLimitService.tryConsume(key, 1L, 3)).as("5번째 요청").isFalse();
        }

        @Test
        @DisplayName("한도가 0이면 첫 요청부터 막는다")
        void blocksEverythingWhenLimitIsZero() {
            assertThat(rateLimitService.tryConsume(uniqueKey(), 1L, 0)).isFalse();
        }

        @Test
        @DisplayName("한도가 1이면 첫 요청만 통과한다")
        void allowsOnlyFirstRequestWhenLimitIsOne() {
            String key = uniqueKey();

            assertThat(rateLimitService.tryConsume(key, 1L, 1)).isTrue();
            assertThat(rateLimitService.tryConsume(key, 1L, 1)).isFalse();
        }
    }

    @Nested
    @DisplayName("격리")
    class Isolation {

        @Test
        @DisplayName("한 사용자가 한도를 다 써도 다른 사용자는 영향받지 않는다")
        void isolatesUsers() {
            String key = uniqueKey();
            for (int i = 0; i < 3; i++) {
                rateLimitService.tryConsume(key, 1L, 2);
            }

            assertThat(rateLimitService.tryConsume(key, 1L, 2)).as("한도를 소진한 사용자").isFalse();
            assertThat(rateLimitService.tryConsume(key, 2L, 2))
                    .as("다른 사용자의 한도는 별도로 센다")
                    .isTrue();
        }

        @Test
        @DisplayName("같은 사용자라도 기능(key)별로 한도를 따로 센다")
        void isolatesKeys() {
            String exhausted = uniqueKey();
            String fresh = uniqueKey();
            rateLimitService.tryConsume(exhausted, 1L, 1);

            assertThat(rateLimitService.tryConsume(exhausted, 1L, 1)).isFalse();
            assertThat(rateLimitService.tryConsume(fresh, 1L, 1))
                    .as("AI 분석 한도를 다 써도 이미지 업로드는 가능해야 한다")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("윈도우")
    class Window {

        @Test
        @DisplayName("첫 요청에 하루짜리 만료시간을 건다")
        void setsOneDayExpiryOnFirstRequest() {
            String key = uniqueKey();

            rateLimitService.tryConsume(key, 1L, 5);

            Long ttlSeconds = redisTemplate.getExpire(redisKey(key, 1L));
            assertThat(ttlSeconds)
                    .as("만료가 걸리지 않으면 카운터가 영구히 남아 사용자가 영영 차단된다")
                    .isGreaterThan(Duration.ofHours(23).toSeconds())
                    .isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
        }

        @Test
        @DisplayName("이후 요청이 만료시간을 연장하지 않는다 - 윈도우가 밀리면 안 된다")
        void doesNotExtendExpiryOnSubsequentRequests() throws InterruptedException {
            String key = uniqueKey();
            rateLimitService.tryConsume(key, 1L, 5);
            Long firstTtl = redisTemplate.getExpire(redisKey(key, 1L));

            Thread.sleep(1100);
            rateLimitService.tryConsume(key, 1L, 5);
            Long secondTtl = redisTemplate.getExpire(redisKey(key, 1L));

            assertThat(secondTtl).isLessThan(firstTtl);
        }

        @Test
        @DisplayName("윈도우가 끝나 카운터가 사라지면 다시 통과한다")
        void recoversAfterWindowExpiry() {
            String key = uniqueKey();
            rateLimitService.tryConsume(key, 1L, 1);
            assertThat(rateLimitService.tryConsume(key, 1L, 1)).isFalse();

            // TTL 만료와 동일한 상태를 만든다 (하루를 기다릴 수 없으므로 키를 삭제한다)
            redisTemplate.delete(redisKey(key, 1L));

            assertThat(rateLimitService.tryConsume(key, 1L, 1))
                    .as("다음 날이 되면 한도가 복구돼야 한다")
                    .isTrue();
        }

        @Test
        @DisplayName("짧은 TTL 을 직접 걸면 만료 후 카운터가 초기화된다")
        void resetsCounterWhenKeyExpires() throws InterruptedException {
            String key = uniqueKey();
            rateLimitService.tryConsume(key, 1L, 1);
            redisTemplate.expire(redisKey(key, 1L), Duration.ofMillis(300));

            Thread.sleep(600);

            assertThat(redisTemplate.hasKey(redisKey(key, 1L))).isFalse();
            assertThat(rateLimitService.tryConsume(key, 1L, 1)).isTrue();
        }
    }
}
