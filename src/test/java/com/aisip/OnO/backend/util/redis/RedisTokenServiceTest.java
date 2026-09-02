package com.aisip.OnO.backend.util.redis;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshToken 저장소와 AccessToken 블랙리스트.
 *
 * <p>남의 리프레시 토큰이 조회되거나 로그아웃한 토큰이 살아 있으면 곧바로 계정 탈취로 이어진다.
 */
@DisplayName("Redis 토큰 서비스")
class RedisTokenServiceTest extends IntegrationTestSupport {

    private static final AtomicLong USER_ID_SEQUENCE = new AtomicLong(100_000);

    @Autowired
    private RedisTokenService redisTokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long uniqueUserId() {
        return USER_ID_SEQUENCE.incrementAndGet();
    }

    @Nested
    @DisplayName("리프레시 토큰")
    class RefreshToken {

        @Test
        @DisplayName("저장한 토큰을 사용자 ID 로 다시 꺼낸다")
        void savesAndReads() {
            Long userId = uniqueUserId();

            redisTokenService.saveRefreshToken(userId, "refresh-token-1", 3600);

            assertThat(redisTokenService.getRefreshToken(userId)).isEqualTo("refresh-token-1");
        }

        @Test
        @DisplayName("저장한 적 없는 사용자는 null 이 나온다")
        void returnsNullWhenNotStored() {
            assertThat(redisTokenService.getRefreshToken(uniqueUserId()))
                    .as("빈 문자열이 아니라 null 이어야 호출부의 null 체크가 동작한다")
                    .isNull();
        }

        @Test
        @DisplayName("재발급하면 이전 토큰을 덮어쓴다")
        void overwritesOnReissue() {
            Long userId = uniqueUserId();
            redisTokenService.saveRefreshToken(userId, "old-token", 3600);

            redisTokenService.saveRefreshToken(userId, "new-token", 3600);

            assertThat(redisTokenService.getRefreshToken(userId)).isEqualTo("new-token");
        }

        @Test
        @DisplayName("삭제하면(로그아웃) 더 이상 조회되지 않는다")
        void deletesToken() {
            Long userId = uniqueUserId();
            redisTokenService.saveRefreshToken(userId, "refresh-token", 3600);

            redisTokenService.deleteRefreshToken(userId);

            assertThat(redisTokenService.getRefreshToken(userId)).isNull();
        }

        @Test
        @DisplayName("만료시간이 지나면 토큰이 사라진다")
        void expiresAfterTtl() throws InterruptedException {
            Long userId = uniqueUserId();
            redisTokenService.saveRefreshToken(userId, "short-lived", 1);

            assertThat(redisTokenService.getRefreshToken(userId)).isEqualTo("short-lived");
            Thread.sleep(1300);

            assertThat(redisTokenService.getRefreshToken(userId))
                    .as("만료된 리프레시 토큰은 재발급에 쓰일 수 없어야 한다")
                    .isNull();
        }

        @Test
        @DisplayName("다른 사용자의 토큰을 읽을 수 없다")
        void isolatesUsers() {
            Long mine = uniqueUserId();
            Long other = uniqueUserId();
            redisTokenService.saveRefreshToken(mine, "my-token", 3600);
            redisTokenService.saveRefreshToken(other, "other-token", 3600);

            assertThat(redisTokenService.getRefreshToken(mine)).isEqualTo("my-token");
            assertThat(redisTokenService.getRefreshToken(other)).isEqualTo("other-token");
        }

        @Test
        @DisplayName("한 사용자의 토큰을 지워도 다른 사용자 토큰은 남는다")
        void deletingOneUserKeepsOthers() {
            Long mine = uniqueUserId();
            Long other = uniqueUserId();
            redisTokenService.saveRefreshToken(mine, "my-token", 3600);
            redisTokenService.saveRefreshToken(other, "other-token", 3600);

            redisTokenService.deleteRefreshToken(mine);

            assertThat(redisTokenService.getRefreshToken(other)).isEqualTo("other-token");
        }

        @Test
        @DisplayName("RT: 접두사로 저장돼 다른 용도의 키와 섞이지 않는다")
        void usesRefreshTokenPrefix() {
            Long userId = uniqueUserId();

            redisTokenService.saveRefreshToken(userId, "token", 3600);

            assertThat(redisTemplate.hasKey("RT:" + userId)).isTrue();
        }
    }

    @Nested
    @DisplayName("액세스 토큰 블랙리스트")
    class Blacklist {

        @Test
        @DisplayName("블랙리스트에 올린 토큰은 무효로 판정된다")
        void marksTokenAsBlacklisted() {
            String accessToken = "access-" + UUID.randomUUID();

            redisTokenService.addToBlacklist(accessToken, 3600);

            assertThat(redisTokenService.isBlacklisted(accessToken)).isTrue();
        }

        @Test
        @DisplayName("올린 적 없는 토큰은 유효하다")
        void treatsUnknownTokenAsValid() {
            assertThat(redisTokenService.isBlacklisted("access-" + UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("토큰 만료시간이 지나면 블랙리스트에서도 사라진다 - 메모리를 무한히 쓰지 않는다")
        void blacklistEntryExpires() throws InterruptedException {
            String accessToken = "access-" + UUID.randomUUID();
            redisTokenService.addToBlacklist(accessToken, 1);

            assertThat(redisTokenService.isBlacklisted(accessToken)).isTrue();
            Thread.sleep(1300);

            assertThat(redisTokenService.isBlacklisted(accessToken)).isFalse();
        }

        @Test
        @DisplayName("한 토큰을 차단해도 다른 토큰은 영향받지 않는다")
        void isolatesTokens() {
            String blocked = "access-" + UUID.randomUUID();
            String alive = "access-" + UUID.randomUUID();
            redisTokenService.addToBlacklist(blocked, 3600);

            assertThat(redisTokenService.isBlacklisted(blocked)).isTrue();
            assertThat(redisTokenService.isBlacklisted(alive)).isFalse();
        }

        @Test
        @DisplayName("블랙리스트 키는 리프레시 토큰 키와 다른 네임스페이스를 쓴다")
        void usesBlacklistPrefix() {
            String accessToken = "access-" + UUID.randomUUID();

            redisTokenService.addToBlacklist(accessToken, 3600);

            assertThat(redisTemplate.hasKey("BL:" + accessToken)).isTrue();
            assertThat(redisTemplate.hasKey("RT:" + accessToken)).isFalse();
        }
    }
}
