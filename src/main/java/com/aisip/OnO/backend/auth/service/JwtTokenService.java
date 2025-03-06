package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
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
    public TokenResponseDto generateTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    public String getSubjectFromToken(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getSubject();
    }

    /**
     * ✅ 리프레시 토큰을 이용한 액세스 토큰 갱신
     */
    public TokenResponseDto refreshAccessToken(String refreshToken) {
        Long userId = Long.parseLong(getSubjectFromToken(refreshToken));
        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }
}
