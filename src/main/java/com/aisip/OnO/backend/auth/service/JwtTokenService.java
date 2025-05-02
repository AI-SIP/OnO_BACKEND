package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.entity.RefreshToken;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.repository.RefreshTokenRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.auth.entity.Authority;
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

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * ✅ 유저 정보를 기반으로 새로운 액세스/리프레시 토큰 생성
     */
    public TokenResponseDto generateTokens(Long userId, Authority authority) {
        String accessToken = jwtTokenizer.createAccessToken(String.valueOf(userId), Map.of("authority", authority));
        String refreshToken = jwtTokenizer.createRefreshToken(String.valueOf(userId), Map.of("authority", authority));

        saveRefreshToken(userId, authority, refreshToken);

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

        RefreshToken storedToken = findRefreshToken(userId, authority);
        validateRefreshTokenMatch(storedToken, refreshToken);

        log.info("userId: {} has : refresh access token", userId);
        return generateTokens(userId, authority);
    }

    private RefreshToken findRefreshToken(Long userId, Authority authority) {
        log.info("userId: {} has find refresh token", userId);

        return refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_FOUND));
    }

    private void validateRefreshTokenMatch(RefreshToken storedToken, String refreshToken) {
        if (!storedToken.getRefreshToken().equals(refreshToken)) {
            refreshTokenRepository.deleteByUserId(storedToken.getUserId());
            throw new ApplicationException(AuthErrorCase.REFRESH_TOKEN_NOT_EQUAL);
        }
    }

    private void saveRefreshToken(Long userId, Authority authority, String refreshToken) {
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.flush();
        refreshTokenRepository.save(RefreshToken.from(userId, authority, refreshToken));
    }
}
