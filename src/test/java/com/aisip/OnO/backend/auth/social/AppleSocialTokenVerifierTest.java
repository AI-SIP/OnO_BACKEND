package com.aisip.OnO.backend.auth.social;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.NetworkException;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Apple identityToken 확인. Apple JWKS 대신 테스트에서 만든 RSA 키를 돌려주는 JwkProvider 를 쓴다.
 */
@DisplayName("Apple identityToken 확인")
class AppleSocialTokenVerifierTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String CLIENT_ID = "com.example.app";
    private static final String KEY_ID = "test-key";
    private static final String SUBJECT = "000123.abcdef.0456";

    private static KeyPair appleKeys;
    private static KeyPair otherKeys;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        appleKeys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private static Jwk jwkOf(RSAPublicKey publicKey) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return Jwk.fromValues(Map.of(
                "kty", "RSA",
                "kid", KEY_ID,
                "use", "sig",
                "alg", "RS256",
                "n", encoder.encodeToString(publicKey.getModulus().toByteArray()),
                "e", encoder.encodeToString(publicKey.getPublicExponent().toByteArray())
        ));
    }

    private static final JwkProvider APPLE_JWKS = keyId -> {
        if (!KEY_ID.equals(keyId)) {
            throw new SigningKeyNotFoundException("no key " + keyId, null);
        }
        return jwkOf((RSAPublicKey) appleKeys.getPublic());
    };

    private static AppleSocialTokenVerifier verifier(JwkProvider provider) {
        return new AppleSocialTokenVerifier(Set.of(CLIENT_ID), ISSUER, provider);
    }

    private static JWTCreator.Builder validClaims() {
        return JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(ISSUER)
                .withAudience(CLIENT_ID)
                .withSubject(SUBJECT)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plusSeconds(600));
    }

    private static String sign(JWTCreator.Builder builder, KeyPair keys) {
        return builder.sign(Algorithm.RSA256((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()));
    }

    private static void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier(APPLE_JWKS).verify(token))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(SocialTokenVerificationException.Reason.INVALID));
    }

    @Test
    @DisplayName("Apple 키로 서명되고 iss, aud 가 맞으면 sub 를 돌려준다")
    void returnsSubjectForValidToken() {
        assertThat(verifier(APPLE_JWKS).verify(sign(validClaims(), appleKeys))).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거절한다")
    void rejectsForeignSignature() {
        assertInvalid(sign(validClaims(), otherKeys));
    }

    @Test
    @DisplayName("aud 가 우리 앱이 아니면 거절한다")
    void rejectsOtherAudience() {
        assertInvalid(sign(validClaims().withAudience("com.other.app"), appleKeys));
    }

    @Test
    @DisplayName("iss 가 Apple 이 아니면 거절한다")
    void rejectsOtherIssuer() {
        assertInvalid(sign(validClaims().withIssuer("https://example.com"), appleKeys));
    }

    @Test
    @DisplayName("만료된 토큰은 거절한다")
    void rejectsExpiredToken() {
        assertInvalid(sign(validClaims()
                .withIssuedAt(Instant.now().minusSeconds(7200))
                .withExpiresAt(Instant.now().minusSeconds(3600)), appleKeys));
    }

    @Test
    @DisplayName("exp 가 없는 토큰은 거절한다")
    void rejectsTokenWithoutExpiry() {
        assertInvalid(sign(JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(ISSUER)
                .withAudience(CLIENT_ID)
                .withSubject(SUBJECT), appleKeys));
    }

    @Test
    @DisplayName("키 목록에 없는 kid 는 거절한다")
    void rejectsUnknownKeyId() {
        assertInvalid(sign(validClaims().withKeyId("unknown"), appleKeys));
    }

    @Test
    @DisplayName("RS256 이 아닌 토큰은 거절한다")
    void rejectsOtherAlgorithm() {
        assertInvalid(validClaims().sign(Algorithm.HMAC256("secret-secret-secret-secret-secret")));
    }

    @Test
    @DisplayName("JWT 형식이 아니면 거절한다")
    void rejectsMalformedToken() {
        assertInvalid("not-a-jwt");
    }

    @Test
    @DisplayName("Apple 키를 받아 오지 못하면 unavailable 로 알린다")
    void reportsUnavailableWhenKeysCannotBeFetched() {
        JwkProvider down = keyId -> {
            throw new NetworkException("down", null);
        };

        assertThatThrownBy(() -> verifier(down).verify(sign(validClaims(), appleKeys)))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    @DisplayName("client id 가 비어 있으면 설정되지 않은 것으로 본다")
    void notConfiguredWithoutClientIds() {
        assertThat(new AppleSocialTokenVerifier(Set.of(), ISSUER, APPLE_JWKS).isConfigured()).isFalse();
        assertThat(new AppleSocialTokenVerifier("", ISSUER, "https://appleid.apple.com/auth/keys").isConfigured()).isFalse();
        assertThat(new AppleSocialTokenVerifier(" com.a , com.b ", ISSUER, "https://appleid.apple.com/auth/keys").isConfigured()).isTrue();
    }
}
