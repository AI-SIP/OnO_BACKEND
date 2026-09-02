package com.aisip.OnO.backend.auth.service;

import com.aisip.OnO.backend.auth.entity.Authority;
import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenizer {

    public static String BEARER_PREFIX = "Bearer ";
    public static long REFRESH_TOKEN_EXPIRATION;

    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final Key accessKey;
    private final Key refreshKey;

    public JwtTokenizer(
            @Value("${jwt.accessToken.expiration}") long accessTokenExpiration,
            @Value("${jwt.refreshToken.expiration}") long refreshTokenExpiration,
            @Value("${jwt.accessToken.secret}") String accessSecret,
            @Value("${jwt.refreshToken.secret}") String refreshSecret
    ) {
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;

        byte[] bytes = Base64.getDecoder().decode(accessSecret);
        this.accessKey = Keys.hmacShaKeyFor(bytes);

        bytes = Base64.getDecoder().decode(refreshSecret);
        this.refreshKey = Keys.hmacShaKeyFor(bytes);
    }

    @PostConstruct
    public void init() {
        REFRESH_TOKEN_EXPIRATION = refreshTokenExpiration;
    }

    public String createAccessToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        return BEARER_PREFIX + Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setId(UUID.randomUUID().toString())
                .setExpiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 액세스 토큰을 검증한다.
     *
     * <p>만료만은 {@link ExpiredJwtException} 을 그대로 올려보낸다.
     * JwtTokenFilter 가 이 예외를 잡아 {@code ACCESS_TOKEN_EXPIRED(1005)} 로 응답하고,
     * 프론트는 1005 를 받아야 토큰 갱신을 시도한다.
     * 만료를 ApplicationException 으로 감싸면 필터가 만료를 구분하지 못해
     * {@code AUTHENTICATION_FAILED(1007)} 로 나가고, 프론트는 갱신 없이 인증 실패로 처리한다.
     */
    public void validateAccessToken(String token) {
        try{
            Jwts.parserBuilder().setSigningKey(accessKey).build().parseClaimsJws(token);
        } catch (ExpiredJwtException e) {
            log.warn("엑세스 토큰 만료: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("엑세스 토큰 검증 실패: {}", e.getMessage());
            throw new ApplicationException(AuthErrorCase.INVALID_ACCESS_TOKEN);
        }
    }

    public void validateRefreshToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(refreshKey).build().parseClaimsJws(token);
        } catch (ExpiredJwtException e) {
            log.warn("리프레시 토큰 만료: {}", e.getMessage());
            throw new ApplicationException(AuthErrorCase.REFRESH_TOKEN_EXPIRED);
        } catch (Exception e) {
            log.warn("리프레시 토큰 검증 실패: {}", e.getMessage());
            throw new ApplicationException(AuthErrorCase.INVALID_REFRESH_TOKEN);
        }
    }

    public Claims getClaimsFromAccessToken(String token) {
        return getClaimsFromToken(token, accessKey);
    }

    public Claims getClaimsFromRefreshToken(String token) {
        return getClaimsFromToken(token, refreshKey);
    }

    private Claims getClaimsFromToken(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserIdFromRefreshToken(String token) {
        Claims claims = getClaimsFromRefreshToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public Authority getAuthorityFromRefreshToken(String token) {
        Claims claims = getClaimsFromRefreshToken(token);
        return Authority.valueOf(claims.get("authority", String.class));
    }

    public Long getUserIdFromAccessToken(String token) {
        Claims claims = getClaimsFromAccessToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public Authority getAuthorityFromAccessToken(String token) {
        Claims claims = getClaimsFromAccessToken(token);
        return Authority.valueOf(claims.get("authority", String.class));
    }

    /**
     * AccessToken의 남은 만료 시간을 초 단위로 반환
     */
    public long getRemainingExpirationTime(String token) {
        Claims claims = getClaimsFromAccessToken(token);
        Date expiration = claims.getExpiration();
        long now = System.currentTimeMillis();
        return Math.max(0, (expiration.getTime() - now) / 1000);
    }

    /**
     * RefreshToken 만료 시간을 초 단위로 반환
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }

    /**
     * RefreshToken의 남은 만료 시간을 초 단위로 반환
     */
    public long getRemainingRefreshExpirationTime(String token) {
        Claims claims = getClaimsFromRefreshToken(token);
        Date expiration = claims.getExpiration();
        long now = System.currentTimeMillis();
        return Math.max(0, (expiration.getTime() - now) / 1000);
    }

    /**
     * AccessToken 만료 시간을 초 단위로 반환
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

}
