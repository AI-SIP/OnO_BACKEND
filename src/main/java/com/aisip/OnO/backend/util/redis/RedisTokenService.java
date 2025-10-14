package com.aisip.OnO.backend.util.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 기반 토큰 관리 서비스
 * - RefreshToken 저장/조회/삭제
 * - AccessToken 블랙리스트 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisSingleDataService redisSingleDataService;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";  // RefreshToken prefix
    private static final String BLACKLIST_PREFIX = "BL:";      // BlackList prefix

    /**
     * RefreshToken을 Redis에 저장
     * @param userId 사용자 ID
     * @param refreshToken 리프레시 토큰
     * @param expiration 만료 시간 (초)
     */
    public void saveRefreshToken(Long userId, String refreshToken, long expiration) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisSingleDataService.setSingleData(key, refreshToken, Duration.ofSeconds(expiration));
        log.info("Saved refresh token for userId: {}", userId);
    }

    /**
     * Redis에서 RefreshToken 조회
     * @param userId 사용자 ID
     * @return RefreshToken (없으면 null)
     */
    public String getRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        String token = redisSingleDataService.getSingleData(key);
        return token.isEmpty() ? null : token;
    }

    /**
     * Redis에서 RefreshToken 삭제
     * @param userId 사용자 ID
     */
    public void deleteRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisSingleDataService.deleteSingleData(key);
        log.info("Deleted refresh token for userId: {}", userId);
    }

    /**
     * AccessToken을 블랙리스트에 추가 (로그아웃 처리)
     * @param accessToken 액세스 토큰
     * @param expiration 남은 만료 시간 (초)
     */
    public void addToBlacklist(String accessToken, long expiration) {
        String key = BLACKLIST_PREFIX + accessToken;
        redisSingleDataService.setSingleData(key, "logout", Duration.ofSeconds(expiration));
        log.info("Added access token to blacklist");
    }

    /**
     * AccessToken이 블랙리스트에 있는지 확인
     * @param accessToken 액세스 토큰
     * @return 블랙리스트에 있으면 true
     */
    public boolean isBlacklisted(String accessToken) {
        String key = BLACKLIST_PREFIX + accessToken;
        String result = redisSingleDataService.getSingleData(key);
        return !result.isEmpty();
    }
}
