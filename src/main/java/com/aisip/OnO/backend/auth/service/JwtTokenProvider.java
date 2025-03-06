package com.aisip.OnO.backend.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${spring.jwt.secret}")
    private String secret;

    @Value("${spring.jwt.expiration}")
    private long expirationTime;

    public String createAccessToken(Long userId) {
        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + (expirationTime * 24)))
                .sign(Algorithm.HMAC512(secret.getBytes()));
    }

    public String createRefreshToken(Long userId) {
        long refreshExpirationTime = expirationTime * 24 * 365; // 365 days
        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .sign(Algorithm.HMAC512(secret.getBytes()));
    }
}