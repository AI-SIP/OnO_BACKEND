package com.aisip.OnO.blackbox.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인 서버 역할. 앱은 설정된 JWKS 주소(WireMock)에서 공개키를 받아 id token 서명을 확인하고,
 * 카카오는 토큰 정보 조회 주소(WireMock)에 물어본다. 실제 구글, 애플, 카카오에는 닿지 않는다.
 */
public final class SocialLogin {

    private static final String KID = "blackbox-key";
    private static final KeyPair KEYS = generate();
    private static volatile boolean jwksRegistered;

    private SocialLogin() {
    }

    /** 앱의 JWKS 조회 결과는 캐시되므로 키는 JVM 동안 하나만 쓰고 처음에 한 번 등록한다. */
    public static synchronized void registerJwks() {
        if (jwksRegistered) {
            return;
        }
        RSAPublicKey publicKey = (RSAPublicKey) KEYS.getPublic();
        Map<String, Object> jwks = Map.of("keys", List.of(Map.of(
                "kty", "RSA", "alg", "RS256", "use", "sig", "kid", KID,
                "n", base64Url(publicKey.getModulus().toByteArray()),
                "e", base64Url(publicKey.getPublicExponent().toByteArray()))));
        WireMock.stub("GET", "/google/oauth2/v3/certs", 200, jwks, 5);
        WireMock.stub("GET", "/apple/auth/keys", 200, jwks, 5);
        jwksRegistered = true;
    }

    public static String googleIdToken(String subject, String audience) {
        return idToken("https://accounts.google.com", subject, audience, KEYS);
    }

    public static String appleIdentityToken(String subject) {
        return idToken("https://appleid.apple.com", subject, BlackboxStack.APPLE_CLIENT_ID, KEYS);
    }

    /** 앱이 모르는 키로 서명한 토큰. 서명 확인에서 걸려야 한다. */
    public static String googleIdTokenSignedByStranger(String subject) {
        return idToken("https://accounts.google.com", subject, BlackboxStack.GOOGLE_CLIENT_ID, generate());
    }

    /** 카카오 토큰 정보 조회에 이 토큰으로 물으면 kakaoUserId, appId 를 돌려주게 한다. */
    public static String kakaoToken(long kakaoUserId, long appId) {
        String token = "kakao-" + kakaoUserId + "-" + appId + "-" + System.nanoTime();
        WireMock.stubRaw(Map.of(
                "priority", 1,
                "request", Map.of(
                        "method", "GET",
                        "urlPath", "/kakao/v1/user/access_token_info",
                        "headers", Map.of("Authorization", Map.of("equalTo", "Bearer " + token))),
                "response", Map.of(
                        "status", 200,
                        "headers", Map.of("Content-Type", "application/json"),
                        "body", Api.write(Map.of("id", kakaoUserId, "expires_in", 3600, "app_id", appId)))));
        return token;
    }

    private static String idToken(String issuer, String subject, String audience, KeyPair keys) {
        Instant now = Instant.now();
        return JWT.create()
                .withKeyId(KID)
                .withIssuer(issuer)
                .withSubject(subject)
                .withAudience(audience)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(600))
                .sign(Algorithm.RSA256((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** BigInteger 의 부호 바이트(0x00)를 떼고 base64url 로 바꾼다. JWK 의 n, e 형식이다. */
    private static String base64Url(byte[] bytes) {
        byte[] unsigned = bytes.length > 1 && bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
