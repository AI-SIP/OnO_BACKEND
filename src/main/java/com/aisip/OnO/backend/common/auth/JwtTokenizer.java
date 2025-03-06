package com.aisip.OnO.backend.common.auth;

import com.aisip.OnO.backend.auth.exception.InvalidTokenException;
import com.aisip.OnO.backend.auth.exception.MissingTokenException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class JwtTokenizer {

    @Value("${spring.jwt.secret}")
    private String secret;

    public boolean validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(secret.getBytes());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);

            //log.info("jwt validate success");
            return true;
        } catch (JWTVerificationException exception) {
            log.warn("token validate failure : " + exception.getMessage());
            return false;
        }
    }

    public String getSubjectFromToken(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getSubject();
    }

    /**
     * ✅ 액세스 토큰 검증
     */
    public void verifyAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new MissingTokenException();
        }

        String accessToken = authorizationHeader.substring(7);
        if (!validateToken(accessToken)) {
            throw new InvalidTokenException();
        }
    }
}
