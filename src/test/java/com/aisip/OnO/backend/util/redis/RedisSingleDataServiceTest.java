package com.aisip.OnO.backend.util.redis;

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
 * 실제 Redis 컨테이너 위에서 검증하는 단일 데이터 저장소.
 *
 * <p>이 구현에는 두 가지 함정이 있어 호출부가 모두 여기에 의존한다.
 * (1) 없는 키를 읽으면 null 이 아니라 빈 문자열을 준다.
 * (2) 실패해도 예외 대신 0 을 반환한다.
 * 두 성질이 바뀌면 토큰/스트릭 캐시가 조용히 깨지므로 계약으로 고정한다.
 */
@DisplayName("Redis 단일 데이터 서비스")
class RedisSingleDataServiceTest extends IntegrationTestSupport {

    @Autowired
    private RedisSingleDataService redisSingleDataService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String uniqueKey() {
        return "test:single:" + UUID.randomUUID();
    }

    @Nested
    @DisplayName("저장과 조회")
    class SaveAndRead {

        @Test
        @DisplayName("저장한 값을 그대로 읽는다")
        void savesAndReads() {
            String key = uniqueKey();

            assertThat(redisSingleDataService.setSingleData(key, "value-1")).isEqualTo(1);
            assertThat(redisSingleDataService.getSingleData(key)).isEqualTo("value-1");
        }

        @Test
        @DisplayName("같은 키에 다시 저장하면 덮어쓴다")
        void overwritesExistingValue() {
            String key = uniqueKey();
            redisSingleDataService.setSingleData(key, "old");

            redisSingleDataService.setSingleData(key, "new");

            assertThat(redisSingleDataService.getSingleData(key)).isEqualTo("new");
        }

        @Test
        @DisplayName("한글과 특수문자도 손실 없이 저장된다")
        void keepsUnicodeValue() {
            String key = uniqueKey();
            String value = "복습 알림 🔔 {\"a\":1}";

            redisSingleDataService.setSingleData(key, value);

            assertThat(redisSingleDataService.getSingleData(key)).isEqualTo(value);
        }

        @Test
        @DisplayName("없는 키를 읽으면 null 이 아니라 빈 문자열이 나온다")
        void returnsEmptyStringForMissingKey() {
            assertThat(redisSingleDataService.getSingleData(uniqueKey()))
                    .as("호출부(RedisTokenService)가 isEmpty() 로 존재 여부를 판단한다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("삭제하면 이후 조회가 빈 문자열이 된다")
        void deletesStoredValue() {
            String key = uniqueKey();
            redisSingleDataService.setSingleData(key, "value");

            assertThat(redisSingleDataService.deleteSingleData(key)).isEqualTo(1);
            assertThat(redisSingleDataService.getSingleData(key)).isEmpty();
        }

        @Test
        @DisplayName("없는 키를 삭제해도 실패로 보지 않는다")
        void deletingMissingKeyIsNoop() {
            assertThat(redisSingleDataService.deleteSingleData(uniqueKey())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("만료")
    class Expiration {

        @Test
        @DisplayName("만료시간을 주면 TTL 이 설정된다")
        void setsTtl() {
            String key = uniqueKey();

            redisSingleDataService.setSingleData(key, "value", Duration.ofMinutes(30));

            assertThat(redisTemplate.getExpire(key))
                    .isGreaterThan(0L)
                    .isLessThanOrEqualTo(Duration.ofMinutes(30).toSeconds());
        }

        @Test
        @DisplayName("TTL 이 지나면 값이 사라진다")
        void expiresAfterTtl() throws InterruptedException {
            String key = uniqueKey();
            redisSingleDataService.setSingleData(key, "value", Duration.ofMillis(300));

            assertThat(redisSingleDataService.getSingleData(key)).isEqualTo("value");
            Thread.sleep(600);

            assertThat(redisSingleDataService.getSingleData(key)).isEmpty();
        }

        @Test
        @DisplayName("만료시간 없이 저장하면 TTL 이 걸리지 않는다")
        void storesWithoutTtlByDefault() {
            String key = uniqueKey();

            redisSingleDataService.setSingleData(key, "value");

            assertThat(redisTemplate.getExpire(key)).as("-1 은 만료 없음").isEqualTo(-1L);
        }
    }

    @Nested
    @DisplayName("직렬화 실패")
    class SerializationFailure {

        @Test
        @DisplayName("문자열이 아닌 값은 저장에 실패하고 0 을 반환한다")
        void returnsZeroWhenValueIsNotString() {
            String key = uniqueKey();

            assertThat(redisSingleDataService.setSingleData(key, 12345))
                    .as("템플릿이 String 직렬화만 하므로 저장이 실패한다. 예외 대신 0 을 준다")
                    .isEqualTo(0);
            assertThat(redisSingleDataService.getSingleData(key)).isEmpty();
        }

        @Test
        @DisplayName("직렬화 실패는 예외로 번지지 않는다")
        void doesNotThrowOnSerializationFailure() {
            assertThat(redisSingleDataService.setSingleData(uniqueKey(), java.util.List.of("a")))
                    .isEqualTo(0);
        }
    }
}
