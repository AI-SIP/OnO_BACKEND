package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Auth.JwtTokenProvider;
import com.aisip.OnO.backend.Dto.Token.TokenResponseDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.exception.ExpiredTokenException;
import com.aisip.OnO.backend.exception.InvalidTokenException;
import com.aisip.OnO.backend.exception.MissingTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * ✅ 유저 정보를 기반으로 새로운 액세스/리프레시 토큰 생성
     */
    public TokenResponseDto generateTokens(UserResponseDto user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        log.info("AccessToken: {}", accessToken);
        log.info("RefreshToken: {}", refreshToken);
        log.info("User {} (ID: {}) has logged in", user.getUserName(), user.getUserId());

        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * ✅ 액세스 토큰 검증
     */
    public void verifyAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new MissingTokenException("Authorization header missing or invalid");
        }

        String accessToken = authorizationHeader.substring(7);
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new InvalidTokenException("Invalid or expired access token");
        }
    }

    /**
     * ✅ 리프레시 토큰을 이용한 액세스 토큰 갱신
     */
    public TokenResponseDto refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ExpiredTokenException("Invalid or expired refresh token");
        }

        Long userId = Long.parseLong(jwtTokenProvider.getSubjectFromToken(refreshToken));
        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }
}
