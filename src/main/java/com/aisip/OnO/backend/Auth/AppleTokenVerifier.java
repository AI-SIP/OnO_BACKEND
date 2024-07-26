package com.aisip.OnO.backend.Auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;

@Component
public class AppleTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";

    public DecodedJWT verifyToken(String idTokenString) throws GeneralSecurityException, IOException {
        try {
            JwkProvider provider = new UrlJwkProvider(new URL(APPLE_KEYS_URL));
            DecodedJWT jwt = JWT.decode(idTokenString);
            Jwk jwk = provider.get(jwt.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(APPLE_ISSUER)
                    .build();

            return verifier.verify(idTokenString);
        } catch (Exception e) {
            throw new GeneralSecurityException("Invalid ID token.");
        }
    }
}