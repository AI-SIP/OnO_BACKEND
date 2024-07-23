package com.aisip.OnO.backend.Auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtTokenProvider {

    private static String SECRET_KEY;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    public void setSecretKey(String SECRET_KEY) {
        JwtTokenProvider.SECRET_KEY = SECRET_KEY;
    }


    private static final long EXPIRATION_TIME = 86400000; // 1 day

    public String createToken(String userId, String email) {
        return JWT.create()
                .withSubject(userId)
                .withClaim("email", email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(Algorithm.HMAC512(SECRET_KEY.getBytes()));
    }
}