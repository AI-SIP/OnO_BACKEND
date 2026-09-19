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
    private static final String USER_BLACKLIST_PREFIX = "UBL:"; // 사용자 단위 BlackList prefix (탈퇴)

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

    /**
     * 사용자 단위로 이미 발급된 액세스 토큰을 전부 막는다 (탈퇴 처리).
     *
     * <p>로그아웃 블랙리스트는 토큰 문자열이 키라서, 탈퇴 시점에 서버가 손에 쥔 그 한 장만 막을 수 있다.
     * 같은 계정이 다른 기기에서 받아 간 토큰은 그대로 남는다. 그래서 사용자 단위 키를 따로 둔다.
     *
     * @param userId 사용자 ID
     * @param expiration 표식을 유지할 기간 (초). 그 계정 앞으로 유효한 자격증명이 남아 있을 수 있는 기간을 넣는다.
     */
    public void blacklistUser(Long userId, long expiration) {
        String key = USER_BLACKLIST_PREFIX + userId;
        redisSingleDataService.setSingleData(key, "withdrawn", Duration.ofSeconds(expiration));
        log.info("Added userId: {} to user blacklist for {}s", userId, expiration);
    }

    /**
     * 사용자 단위 블랙리스트에 올라 있는지 확인
     * @param userId 사용자 ID
     * @return 블랙리스트에 있으면 true
     */
    public boolean isUserBlacklisted(Long userId) {
        String key = USER_BLACKLIST_PREFIX + userId;
        String result = redisSingleDataService.getSingleData(key);
        return !result.isEmpty();
    }
}
