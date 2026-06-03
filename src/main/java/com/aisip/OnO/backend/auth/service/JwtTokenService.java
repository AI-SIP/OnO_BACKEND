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
     * - 로그인/회원가입 시마다 refresh token도 새로 발급해 만료 시간을 갱신
     */
    public TokenResponseDto generateTokens(Long userId, Authority authority) {
        String accessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));
        String refreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));

        refreshTokenRepository.save(RefreshToken.from(userId, authority, refreshToken));

        log.info("userId: {} has : generate token with authority: {}", userId, authority);
        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * ✅ 리프레시 토큰을 이용한 토큰 갱신
     * - access token과 refresh token을 함께 재발급해 세션 만료 시간을 갱신
     */
    public TokenResponseDto refreshAccessToken(String refreshToken) {
        jwtTokenizer.validateRefreshToken(refreshToken);

        Long userId = jwtTokenizer.getUserIdFromRefreshToken(refreshToken);
        Authority authority = jwtTokenizer.getAuthorityFromRefreshToken(refreshToken);

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND));

        String newAccessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));
        String newRefreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));

        refreshTokenEntity.updateToken(authority, newRefreshToken);
        refreshTokenRepository.save(refreshTokenEntity);

        log.info("userId: {} has : refresh access token and rotate refresh token", userId);
        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }

    /**
     * ✅ 로그아웃 처리 (Redis에서 RefreshToken 삭제 및 AccessToken 블랙리스트 추가)
     */
    public void logout(String accessToken, Long userId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteByRefreshToken(refreshToken);
        }
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
