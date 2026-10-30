package com.aisip.OnO.backend.auth.social;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwk.NetworkException;
import com.auth0.jwk.RateLimitReachedException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Google ID 토큰을 확인한다.
 *
 * <p>ID 토큰은 Google 이 RS256 으로 서명한 JWT 다. 헤더의 kid 로 Google JWKS 에서 공개키를 골라 서명을 확인하고,
 * iss 가 Google 인지, aud 가 우리 OAuth 클라이언트 ID 인지, 만료되지 않았는지 본다.
 *
 * <p>google-api-client 의 GoogleIdTokenVerifier 는 공개키를 받을 때 연결과 읽기 대기를 20초씩 기다리고
 * 그동안 다른 요청도 같은 락에서 기다린다. 요청 스레드가 오래 묶이지 않도록 Apple 과 같이 jwks-rsa 로
 * 2초/3초 안에 끝내고, 키는 메모리에 캐시한다.
 */
@Component
public class GoogleSocialTokenVerifier implements SocialTokenVerifier {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;
    private static final long CLOCK_LEEWAY_SECONDS = 60;
    private static final String[] ISSUERS = {"https://accounts.google.com", "accounts.google.com"};

    private final Set<String> clientIds;
    private final JwkProvider jwkProvider;

    @Autowired
    public GoogleSocialTokenVerifier(
            @Value("${auth.social-verification.google.client-ids:}") String clientIds,
            @Value("${auth.social-verification.google.jwks-url:https://www.googleapis.com/oauth2/v3/certs}") String jwksUrl
    ) {
        this(SocialVerificationSettings.parseList(clientIds), buildJwkProvider(jwksUrl));
    }

    GoogleSocialTokenVerifier(Set<String> clientIds, JwkProvider jwkProvider) {
        this.clientIds = clientIds;
        this.jwkProvider = jwkProvider;
    }

    private static JwkProvider buildJwkProvider(String jwksUrl) {
        try {
            return new JwkProviderBuilder(URI.create(jwksUrl).toURL())
                    .cached(10, 6, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .timeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("auth.social-verification.google.jwks-url 이 올바른 URL 이 아니다", e);
        }
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.GOOGLE;
    }

    @Override
    public boolean isConfigured() {
        return !clientIds.isEmpty();
    }

    @Override
    public String verify(String token) {
        DecodedJWT decoded;
        try {
            decoded = JWT.decode(token);
        } catch (JWTVerificationException e) {
            throw SocialTokenVerificationException.invalid("google id token is malformed", e);
        }

        if (!"RS256".equals(decoded.getAlgorithm())) {
            throw SocialTokenVerificationException.invalid("google id token has unexpected algorithm");
        }

        RSAPublicKey publicKey = findPublicKey(decoded.getKeyId());

        try {
            JWT.require(Algorithm.RSA256(publicKey, null))
                    .withIssuer(ISSUERS)
                    .withAnyOfAudience(clientIds.toArray(String[]::new))
                    .withClaimPresence("exp")
                    .acceptLeeway(CLOCK_LEEWAY_SECONDS)
                    .build()
                    .verify(decoded);
        } catch (JWTVerificationException e) {
            throw SocialTokenVerificationException.invalid("google id token rejected", e);
        }

        String subject = decoded.getSubject();
        if (subject == null || subject.isBlank()) {
            throw SocialTokenVerificationException.invalid("google id token has no subject");
        }
        return subject;
    }

    private RSAPublicKey findPublicKey(String keyId) {
        try {
            Jwk jwk = jwkProvider.get(keyId);
            PublicKey publicKey = jwk.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw SocialTokenVerificationException.invalid("google public key is not RSA");
            }
            return rsaPublicKey;
        } catch (NetworkException | RateLimitReachedException e) {
            // NetworkException 은 SigningKeyNotFoundException 의 하위 타입이라 먼저 잡는다.
            throw SocialTokenVerificationException.unavailable("google public keys unavailable", e);
        } catch (JwkException e) {
            // 키 목록에 없는 kid 다.
            throw SocialTokenVerificationException.invalid("google public key not found", e);
        }
    }
}
