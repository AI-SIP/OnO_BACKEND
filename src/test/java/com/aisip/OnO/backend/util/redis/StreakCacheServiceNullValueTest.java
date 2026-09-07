package com.aisip.OnO.backend.util.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 캐시 구현이 null 을 돌려줄 때의 스트릭 캐시 동작.
 *
 * <p>현재 {@link RedisSingleDataServiceImpl} 은 미스일 때 빈 문자열을 주지만
 * {@link RedisSingleDataService} 계약상 null 이 올 수도 있다. 여기서 NPE 가 나면
 * 캐시 미스가 학습 스트릭 조회 실패로 번진다. 실제 Redis 를 쓰는 나머지 경로는
 * {@code StreakCacheServiceTest} 가 담당하고, 이 클래스는 그 한 갈래만 본다.
 */
@DisplayName("스트릭 캐시 - 캐시가 null 을 돌려줄 때")
class StreakCacheServiceNullValueTest {

    private final RedisSingleDataService redisSingleDataService = mock(RedisSingleDataService.class);
    private final StreakCacheService streakCacheService =
            new StreakCacheService(redisSingleDataService, new ObjectMapper());

    @Test
    @DisplayName("null 을 받으면 예외 없이 캐시 미스로 처리한다")
    void treatsNullAsCacheMiss() {
        given(redisSingleDataService.getSingleData("STREAK:7")).willReturn(null);

        Optional<TreeSet<LocalDate>> cached = streakCacheService.get(7L);

        assertThat(cached)
                .as("캐시 미스가 조회 실패로 번지면 안 된다")
                .isEmpty();
    }
}
