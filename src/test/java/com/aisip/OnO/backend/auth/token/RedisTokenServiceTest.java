package com.aisip.OnO.backend.auth.token;

import com.aisip.OnO.backend.util.redis.RedisSingleDataService;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Redis 토큰 저장소의 키 규칙·TTL 계약과, Redis 장애 시 동작을 고정한다.
 *
 * <p>블랙리스트 조회는 인증 필터가 <b>모든 인증 요청마다</b> 호출한다.
 * 여기서 예외가 새어나가면 Redis 장애가 곧 전체 사용자 인증 실패로 번진다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenService")
class RedisTokenServiceTest {

    private static final Long USER_ID = 42L;
    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.payload.signature";

    @Mock
    private RedisSingleDataService redisSingleDataService;

    @InjectMocks
    private RedisTokenService redisTokenService;

    @Nested
    @DisplayName("리프레시 토큰 저장소")
    class RefreshTokenStore {

        @Test
        @DisplayName("RT: 프리픽스 + userId 키에 TTL 과 함께 저장한다")
        void savesWithPrefixedKeyAndTtl() {
            redisTokenService.saveRefreshToken(USER_ID, "refresh-token-value", 604_800L);

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(redisSingleDataService).setSingleData(eq("RT:42"), eq("refresh-token-value"), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(604_800L));
        }

        @Test
        @DisplayName("저장된 토큰을 userId 로 되읽는다")
        void readsBackByUserId() {
            given(redisSingleDataService.getSingleData("RT:42")).willReturn("refresh-token-value");

            assertThat(redisTokenService.getRefreshToken(USER_ID)).isEqualTo("refresh-token-value");
        }

        @Test
        @DisplayName("저장된 값이 없으면 빈 문자열이 아니라 null 을 돌려준다")
        void returnsNullWhenAbsent() {
            given(redisSingleDataService.getSingleData("RT:42")).willReturn("");

            assertThat(redisTokenService.getRefreshToken(USER_ID))
                    .as("빈 문자열이 그대로 나가면 호출부가 '토큰 있음' 으로 오해한다")
                    .isNull();
        }

        @Test
        @DisplayName("삭제는 같은 키 규칙으로 지운다")
        void deletesWithSameKey() {
            redisTokenService.deleteRefreshToken(USER_ID);

            verify(redisSingleDataService).deleteSingleData("RT:42");
        }

        @Test
        @DisplayName("사용자마다 키가 분리되어 서로의 토큰을 덮어쓰지 않는다")
        void isolatesKeysPerUser() {
            given(redisSingleDataService.getSingleData("RT:42")).willReturn("mine");
            given(redisSingleDataService.getSingleData("RT:43")).willReturn("theirs");

            assertThat(redisTokenService.getRefreshToken(42L)).isEqualTo("mine");
            assertThat(redisTokenService.getRefreshToken(43L)).isEqualTo("theirs");
        }
    }

    @Nested
    @DisplayName("액세스 토큰 블랙리스트")
    class Blacklist {

        @Test
        @DisplayName("BL: 프리픽스 키에 남은 만료 시간만큼만 등록한다")
        void addsWithRemainingTtl() {
            redisTokenService.addToBlacklist(ACCESS_TOKEN, 1_200L);

            verify(redisSingleDataService)
                    .setSingleData("BL:" + ACCESS_TOKEN, "logout", Duration.ofSeconds(1_200L));
        }

        @Test
        @DisplayName("등록된 토큰은 블랙리스트로 판정한다")
        void detectsBlacklistedToken() {
            given(redisSingleDataService.getSingleData("BL:" + ACCESS_TOKEN)).willReturn("logout");

            assertThat(redisTokenService.isBlacklisted(ACCESS_TOKEN)).isTrue();
        }

        @Test
        @DisplayName("등록되지 않은 토큰은 통과시킨다")
        void allowsUnknownToken() {
            given(redisSingleDataService.getSingleData(anyString())).willReturn("");

            assertThat(redisTokenService.isBlacklisted(ACCESS_TOKEN)).isFalse();
        }
    }

    @Nested
    @DisplayName("Redis 장애")
    class RedisOutage {

        /**
         * 현재 구현은 조회 실패를 그대로 던진다. 인증 필터는 이 예외를 잡아
         * AUTHENTICATION_FAILED 로 처리하므로, Redis 가 죽으면 정상 토큰을 가진
         * 사용자도 401 을 받는다. 이 테스트는 그 동작을 명시적으로 못 박는다.
         */
        @Test
        @DisplayName("블랙리스트 조회 실패는 그대로 전파된다 — Redis 장애가 인증 실패로 번지는 지점")
        void propagatesLookupFailure() {
            given(redisSingleDataService.getSingleData(anyString()))
                    .willThrow(new RedisConnectionFailureException("redis down"));

            assertThatThrownBy(() -> redisTokenService.isBlacklisted(ACCESS_TOKEN))
                    .isInstanceOf(RedisConnectionFailureException.class);
        }

        @Test
        @DisplayName("쓰기 실패는 예외 없이 흡수된다")
        void swallowsWriteFailure() {
            given(redisSingleDataService.setSingleData(anyString(), any(), any(Duration.class))).willReturn(0);

            redisTokenService.addToBlacklist(ACCESS_TOKEN, 60L);

            verify(redisSingleDataService).setSingleData(anyString(), any(), any(Duration.class));
        }
    }
}
