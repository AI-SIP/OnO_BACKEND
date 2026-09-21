package com.aisip.OnO.backend.util.redis;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 연속 학습일(스트릭) 캐시.
 *
 * <p>캐시는 실패하더라도 조용히 비켜서야 한다. 여기서 예외가 나면 캘린더 조회 전체가 500 이 된다.
 */
@DisplayName("스트릭 캐시")
class StreakCacheServiceTest extends IntegrationTestSupport {

    private static final AtomicLong USER_ID_SEQUENCE = new AtomicLong(500_000);

    @Autowired
    private StreakCacheService streakCacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long uniqueUserId() {
        return USER_ID_SEQUENCE.incrementAndGet();
    }

    private TreeSet<LocalDate> dates(String... isoDates) {
        TreeSet<LocalDate> result = new TreeSet<>();
        for (String isoDate : isoDates) {
            result.add(LocalDate.parse(isoDate));
        }
        return result;
    }

    @Nested
    @DisplayName("저장과 조회")
    class SaveAndRead {

        @Test
        @DisplayName("저장한 날짜 집합을 정렬된 상태로 되돌려준다")
        void restoresSortedDates() {
            Long userId = uniqueUserId();
            streakCacheService.put(userId, dates("2026-03-03", "2026-03-01", "2026-03-02"));

            Optional<TreeSet<LocalDate>> cached = streakCacheService.get(userId);

            assertThat(cached).isPresent();
            assertThat(cached.get())
                    .as("스트릭 계산은 정렬 순서에 의존한다")
                    .containsExactly(
                            LocalDate.parse("2026-03-01"),
                            LocalDate.parse("2026-03-02"),
                            LocalDate.parse("2026-03-03"));
        }

        @Test
        @DisplayName("빈 집합도 캐시 미스와 구분해 저장된다")
        void storesEmptySet() {
            Long userId = uniqueUserId();

            streakCacheService.put(userId, new TreeSet<>());

            assertThat(streakCacheService.get(userId))
                    .as("학습 기록이 없는 사용자도 캐시 히트로 처리돼야 매번 DB 를 때리지 않는다")
                    .contains(new TreeSet<>());
        }

        @Test
        @DisplayName("캐시에 없는 사용자는 빈 Optional 을 준다")
        void returnsEmptyOnCacheMiss() {
            assertThat(streakCacheService.get(uniqueUserId())).isEmpty();
        }

        @Test
        @DisplayName("다시 저장하면 최신 값으로 덮어쓴다")
        void overwritesPreviousValue() {
            Long userId = uniqueUserId();
            streakCacheService.put(userId, dates("2026-03-01"));

            streakCacheService.put(userId, dates("2026-03-01", "2026-03-02"));

            assertThat(streakCacheService.get(userId)).hasValueSatisfying(
                    cached -> assertThat(cached).hasSize(2));
        }
    }

    @Nested
    @DisplayName("무효화")
    class Eviction {

        @Test
        @DisplayName("evict 하면 캐시 미스가 된다")
        void evictsCache() {
            Long userId = uniqueUserId();
            streakCacheService.put(userId, dates("2026-03-01"));

            streakCacheService.evict(userId);

            assertThat(streakCacheService.get(userId)).isEmpty();
        }

        @Test
        @DisplayName("캐시가 없는 사용자를 evict 해도 예외가 나지 않는다")
        void evictingMissingCacheIsSafe() {
            assertThatCode(() -> streakCacheService.evict(uniqueUserId())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("격리와 만료")
    class IsolationAndExpiry {

        @Test
        @DisplayName("사용자별로 다른 키에 저장돼 서로의 스트릭을 보지 않는다")
        void isolatesUsers() {
            Long mine = uniqueUserId();
            Long other = uniqueUserId();
            streakCacheService.put(mine, dates("2026-03-01", "2026-03-02"));
            streakCacheService.put(other, dates("2026-01-01"));

            assertThat(streakCacheService.get(mine)).hasValueSatisfying(
                    cached -> assertThat(cached).hasSize(2));
            assertThat(streakCacheService.get(other)).hasValueSatisfying(
                    cached -> assertThat(cached).containsExactly(LocalDate.parse("2026-01-01")));
        }

        @Test
        @DisplayName("한 사용자를 evict 해도 다른 사용자 캐시는 남는다")
        void evictionIsPerUser() {
            Long mine = uniqueUserId();
            Long other = uniqueUserId();
            streakCacheService.put(mine, dates("2026-03-01"));
            streakCacheService.put(other, dates("2026-03-01"));

            streakCacheService.evict(mine);

            assertThat(streakCacheService.get(other)).isPresent();
        }

        @Test
        @DisplayName("자정까지의 TTL 이 걸린다 - 날짜가 바뀌면 자동으로 무효화된다")
        void expiresAtNextMidnight() {
            Long userId = uniqueUserId();

            streakCacheService.put(userId, dates("2026-03-01"));

            Long ttlSeconds = redisTemplate.getExpire("STREAK:" + userId);
            assertThat(ttlSeconds)
                    .as("TTL 이 없으면 날짜가 바뀌어도 어제 기준 스트릭을 계속 보여준다")
                    .isGreaterThan(0L)
                    .isLessThanOrEqualTo(Duration.ofDays(1).toSeconds());
        }
    }

    @Nested
    @DisplayName("역직렬화 실패")
    class DeserializationFailure {

        @Test
        @DisplayName("캐시에 깨진 JSON 이 있으면 예외 대신 캐시 미스로 처리한다")
        void treatsCorruptedCacheAsMiss() {
            Long userId = uniqueUserId();
            redisTemplate.opsForValue().set("STREAK:" + userId, "{깨진 JSON");

            assertThat(streakCacheService.get(userId))
                    .as("캐시 오염이 학습 캘린더 500 으로 번지면 안 된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("형식이 다른 JSON 이 있어도 캐시 미스로 처리한다")
        void treatsUnexpectedJsonAsMiss() {
            Long userId = uniqueUserId();
            redisTemplate.opsForValue().set("STREAK:" + userId, "{\"unexpected\":true}");

            assertThat(streakCacheService.get(userId)).isEmpty();
        }
    }
}
