package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.RefreshToken;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.util.redis.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JwtTokenService {

    private final JwtTokenizer jwtTokenizer;
    private final RedisTokenService redisTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * ✅ 유저 정보를 기반으로 액세스 토큰 생성
     * - 기존 리프레시 토큰이 있으면 재사용
     * - 없을 때만 신규 리프레시 토큰 발급/저장
     */
    public TokenResponseDto generateTokens(Long userId, Authority authority) {
        String accessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));
        String refreshToken;

        // DB에 기존 RefreshToken이 있으면 검증 후 재사용, 만료/무효면 재발급
        RefreshToken existingRefreshToken = refreshTokenRepository.findByUserId(userId).orElse(null);
        if (existingRefreshToken != null) {
            String existingTokenValue = existingRefreshToken.getRefreshToken();
            try {
                jwtTokenizer.validateRefreshToken(existingTokenValue);
                refreshToken = existingTokenValue;
                log.info("Reusing existing refresh token for userId: {}", userId);
            } catch (ApplicationException e) {
                refreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));
                refreshTokenRepository.deleteByUserId(userId);
                refreshTokenRepository.save(RefreshToken.from(userId, authority, refreshToken));
                log.info("Existing refresh token was expired/invalid. Issued a new one for userId: {}", userId);
            }
        } else {
            refreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));
            RefreshToken refreshTokenEntity = RefreshToken.from(userId, authority, refreshToken);
            refreshTokenRepository.save(refreshTokenEntity);
            log.info("Created new refresh token for userId: {}", userId);
        }

        // Redis에 RefreshToken 캐싱 (토큰 실제 남은 만료시간 기준)
        long expiration = jwtTokenizer.getRemainingRefreshExpirationTime(refreshToken);
        redisTokenService.saveRefreshToken(userId, refreshToken, expiration);

        log.info("userId: {} has : generate token with authority: {}", userId, authority);
        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * ✅ 리프레시 토큰을 이용한 액세스 토큰 갱신
     * - refresh token은 재사용하고 access token만 재발급
     */
    public TokenResponseDto refreshAccessToken(String refreshToken) {
        jwtTokenizer.validateRefreshToken(refreshToken);

        Long userId = jwtTokenizer.getUserIdFromRefreshToken(refreshToken);
        Authority authority = jwtTokenizer.getAuthorityFromRefreshToken(refreshToken);

        // 1. Redis에서 RefreshToken 조회
        String storedToken = redisTokenService.getRefreshToken(userId);

        // 2. Redis에 없으면 DB에서 조회 후 Redis에 캐싱
        if (storedToken == null) {
            RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND));

            storedToken = refreshTokenEntity.getRefreshToken();
            jwtTokenizer.validateRefreshToken(storedToken);

            // DB에서 찾은 토큰을 Redis에 다시 캐싱
            long expiration = jwtTokenizer.getRemainingRefreshExpirationTime(storedToken);
            redisTokenService.saveRefreshToken(userId, storedToken, expiration);

            log.info("RefreshToken cache miss - loaded from DB for userId: {}", userId);
        }

        // 3. RefreshToken 일치 여부 확인
        if (!storedToken.equals(refreshToken)) {
            log.warn("RefreshToken mismatch for userId: {}", userId);
            throw new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_EQUAL);
        }

        // 4. access token만 재발급 (refresh token은 만료 연장/교체하지 않음)
        String newAccessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));

        long expiration = jwtTokenizer.getRemainingRefreshExpirationTime(refreshToken);
        redisTokenService.saveRefreshToken(userId, refreshToken, expiration);

        log.info("userId: {} has : refresh access token (refresh token reused)", userId);
        return new TokenResponseDto(newAccessToken, refreshToken);
    }

    /**
     * ✅ 로그아웃 처리 (Redis에서 RefreshToken 삭제 및 AccessToken 블랙리스트 추가)
     */
    public void logout(String accessToken, Long userId) {
        // RefreshToken 삭제
        redisTokenService.deleteRefreshToken(userId);

        // AccessToken을 블랙리스트에 추가 (만료된 토큰이면 블랙리스트 추가 안 함)
        try {
            String tokenWithoutBearer = accessToken.replace(JwtTokenizer.BEARER_PREFIX, "");
            long remainingExpiration = jwtTokenizer.getRemainingExpirationTime(tokenWithoutBearer);

            // 만료 시간이 남아있을 때만 블랙리스트에 추가
            if (remainingExpiration > 0) {
                redisTokenService.addToBlacklist(tokenWithoutBearer, remainingExpiration);
            }
        } catch (Exception e) {
            // 만료된 토큰이거나 잘못된 토큰이면 무시 (어차피 사용 불가)
            log.debug("Token already expired or invalid during logout: {}", e.getMessage());
        }

        log.info("userId: {} has logged out", userId);
    }
}
