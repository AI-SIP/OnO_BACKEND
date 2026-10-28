package com.aisip.OnO.backend.util.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * getSingleData 가 GET 을 한 번만 보내는지 본다(#410).
 * 실제 Redis 로는 두 GET 사이의 만료를 재현할 수 없어, 첫 GET 은 값을 주고 두 번째 GET 은 null 을 주는
 * 상황을 mock 으로 만든다. 예전 코드는 이때 문자열 "null" 을 돌려줬다.
 */
@DisplayName("Redis 단일 데이터 조회 GET 횟수")
@SuppressWarnings("unchecked")
class RedisSingleDataServiceSingleGetTest {

    private static final String KEY = "test:single:get-once";

    private ValueOperations<String, Object> valueOperations;
    private RedisSingleDataServiceImpl service;

    @BeforeEach
    void setUp() {
        RedisHandler redisHandler = mock(RedisHandler.class);
        valueOperations = mock(ValueOperations.class);
        when(redisHandler.getValueOperations()).thenReturn(valueOperations);
        service = new RedisSingleDataServiceImpl(redisHandler, mock(RedisConfig.class));
    }

    @Test
    @DisplayName("값이 있는 키는 GET 한 번으로 값을 돌려준다")
    void readsExistingKeyWithSingleGet() {
        when(valueOperations.get(KEY)).thenReturn("value-1");

        assertThat(service.getSingleData(KEY)).isEqualTo("value-1");
        verify(valueOperations, times(1)).get(KEY);
    }

    @Test
    @DisplayName("없는 키는 GET 한 번 뒤 빈 문자열을 돌려준다")
    void returnsEmptyStringForMissingKeyWithSingleGet() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(service.getSingleData(KEY)).isEmpty();
        verify(valueOperations, times(1)).get(KEY);
    }

    @Test
    @DisplayName("다음 GET 에서 키가 사라져도 문자열 \"null\" 이 나가지 않는다")
    void doesNotReturnNullStringWhenKeyExpiresBetweenReads() {
        // 첫 GET 은 값, 그다음 GET 은 만료로 null. 한 번만 읽으면 첫 값이 그대로 나간다.
        when(valueOperations.get(KEY)).thenReturn("value-1", (Object) null);

        String result = service.getSingleData(KEY);

        assertThat(result)
                .as("예전 코드는 두 번째 GET 의 null 을 String.valueOf 로 바꿔 \"null\" 을 돌려줬다")
                .isNotEqualTo("null")
                .isEqualTo("value-1");
    }
}
