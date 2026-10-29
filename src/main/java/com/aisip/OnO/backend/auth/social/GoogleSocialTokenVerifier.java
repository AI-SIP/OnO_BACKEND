package com.aisip.OnO.backend.auth.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Google ID 토큰을 확인한다.
 *
 * <p>google-api-client 의 {@link GoogleIdTokenVerifier} 가 Google 공개키로 서명을 확인하고
 * iss(accounts.google.com), exp, aud 를 본다. aud 는 앱이 토큰을 받을 때 쓴 OAuth client id 라서
 * 플랫폼마다 다르다(Android 는 web client id, iOS 는 iOS client id). 그래서 목록으로 받는다.
 * 공개키는 응답의 Cache-Control 동안 메모리에 캐시된다.
 */
@Component
public class GoogleSocialTokenVerifier implements SocialTokenVerifier {

    private final Set<String> clientIds;
    private final GoogleIdTokenVerifier idTokenVerifier;

    @Autowired
    public GoogleSocialTokenVerifier(
            @Value("${auth.social-verification.google.client-ids:}") String clientIds
    ) {
        this(SocialVerificationSettings.parseList(clientIds));
    }

    private GoogleSocialTokenVerifier(Set<String> clientIds) {
        this(clientIds, clientIds.isEmpty()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(clientIds)
                        .build());
    }

    GoogleSocialTokenVerifier(Set<String> clientIds, GoogleIdTokenVerifier idTokenVerifier) {
        this.clientIds = clientIds;
        this.idTokenVerifier = idTokenVerifier;
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.GOOGLE;
    }

    @Override
    public boolean isConfigured() {
        return !clientIds.isEmpty() && idTokenVerifier != null;
    }

    @Override
    public String verify(String token) {
        GoogleIdToken idToken;
        try {
            idToken = idTokenVerifier.verify(token);
        } catch (IOException e) {
            // 공개키를 받아 오지 못했다. 토큰 탓이 아니다.
            throw SocialTokenVerificationException.unavailable("google public keys unavailable", e);
        } catch (Exception e) {
            // 형식이 깨진 토큰은 IllegalArgumentException, 서명 계산 실패는 GeneralSecurityException 으로 온다.
            throw SocialTokenVerificationException.invalid("google id token rejected", e);
        }

        if (idToken == null) {
            throw SocialTokenVerificationException.invalid("google id token rejected");
        }
        String subject = idToken.getPayload().getSubject();
        if (subject == null || subject.isBlank()) {
            throw SocialTokenVerificationException.invalid("google id token has no subject");
        }
        return subject;
    }
}
