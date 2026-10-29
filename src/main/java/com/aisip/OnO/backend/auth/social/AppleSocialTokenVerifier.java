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
 * Apple identityToken 을 확인한다.
 *
 * <p>identityToken 은 Apple 이 RS256 으로 서명한 JWT 다. 헤더의 kid 로 Apple JWKS 에서 공개키를 골라
 * 서명을 확인하고, iss 가 Apple 인지, aud 가 우리 앱 번들 ID(또는 Service ID)인지, 만료되지 않았는지 본다.
 * 공개키는 jwks-rsa 가 메모리에 캐시하고, 키를 찾지 못해 다시 받는 횟수는 분당 10번으로 묶는다.
 */
@Component
public class AppleSocialTokenVerifier implements SocialTokenVerifier {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;
    private static final long CLOCK_LEEWAY_SECONDS = 60;

    private final Set<String> clientIds;
    private final String issuer;
    private final JwkProvider jwkProvider;

    @Autowired
    public AppleSocialTokenVerifier(
            @Value("${auth.social-verification.apple.client-ids:}") String clientIds,
            @Value("${auth.social-verification.apple.issuer:https://appleid.apple.com}") String issuer,
            @Value("${auth.social-verification.apple.jwks-url:https://appleid.apple.com/auth/keys}") String jwksUrl
    ) {
        this(SocialVerificationSettings.parseList(clientIds), issuer, buildJwkProvider(jwksUrl));
    }

    AppleSocialTokenVerifier(Set<String> clientIds, String issuer, JwkProvider jwkProvider) {
        this.clientIds = clientIds;
        this.issuer = issuer;
        this.jwkProvider = jwkProvider;
    }

    private static JwkProvider buildJwkProvider(String jwksUrl) {
        try {
            return new JwkProviderBuilder(URI.create(jwksUrl).toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .timeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("auth.social-verification.apple.jwks-url 이 올바른 URL 이 아니다", e);
        }
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.APPLE;
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
            throw SocialTokenVerificationException.invalid("apple identity token is malformed", e);
        }

        if (!"RS256".equals(decoded.getAlgorithm())) {
            throw SocialTokenVerificationException.invalid("apple identity token has unexpected algorithm");
        }

        RSAPublicKey publicKey = findPublicKey(decoded.getKeyId());

        try {
            JWT.require(Algorithm.RSA256(publicKey, null))
                    .withIssuer(issuer)
                    .withAnyOfAudience(clientIds.toArray(String[]::new))
                    .withClaimPresence("exp")
                    .acceptLeeway(CLOCK_LEEWAY_SECONDS)
                    .build()
                    .verify(decoded);
        } catch (JWTVerificationException e) {
            throw SocialTokenVerificationException.invalid("apple identity token rejected", e);
        }

        String subject = decoded.getSubject();
        if (subject == null || subject.isBlank()) {
            throw SocialTokenVerificationException.invalid("apple identity token has no subject");
        }
        return subject;
    }

    private RSAPublicKey findPublicKey(String keyId) {
        try {
            Jwk jwk = jwkProvider.get(keyId);
            PublicKey publicKey = jwk.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw SocialTokenVerificationException.invalid("apple public key is not RSA");
            }
            return rsaPublicKey;
        } catch (NetworkException | RateLimitReachedException e) {
            // NetworkException 은 SigningKeyNotFoundException 의 하위 타입이라 먼저 잡는다.
            throw SocialTokenVerificationException.unavailable("apple public keys unavailable", e);
        } catch (JwkException e) {
            // 키 목록에 없는 kid 다.
            throw SocialTokenVerificationException.invalid("apple public key not found", e);
        }
    }
}
