package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.RefreshToken;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.mission.service.MissionLogService;
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
     * ✅ 유저 정보를 기반으로 새로운 액세스/리프레시 토큰 생성 (Redis + DB)
     */
    public TokenResponseDto generateTokens(Long userId, Authority authority) {
        String accessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));
        String refreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));

        // DB에 RefreshToken 저장 (기존 토큰이 있으면 삭제 후 저장)
        refreshTokenRepository.deleteByUserId(userId);
        RefreshToken refreshTokenEntity = RefreshToken.from(userId, authority, refreshToken);
        refreshTokenRepository.save(refreshTokenEntity);

        // Redis에 RefreshToken 캐싱
        long expiration = jwtTokenizer.getRefreshTokenExpirationSeconds();
        redisTokenService.saveRefreshToken(userId, refreshToken, expiration);

        log.info("userId: {} has : generate token with authority: {}", userId, authority);
        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * ✅ 리프레시 토큰을 이용한 액세스 토큰 갱신
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

            // DB에서 찾은 토큰을 Redis에 다시 캐싱
            long expiration = jwtTokenizer.getRefreshTokenExpirationSeconds();
            redisTokenService.saveRefreshToken(userId, storedToken, expiration);

            log.info("RefreshToken cache miss - loaded from DB for userId: {}", userId);
        }

        // 3. RefreshToken 일치 여부 확인
        if (!storedToken.equals(refreshToken)) {
            redisTokenService.deleteRefreshToken(userId);
            refreshTokenRepository.deleteByUserId(userId);
            throw new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_EQUAL);
        }

        log.info("userId: {} has : refresh access token", userId);
        return generateTokens(userId, authority);
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
