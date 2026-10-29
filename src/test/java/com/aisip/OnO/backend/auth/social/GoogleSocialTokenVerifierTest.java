package com.aisip.OnO.backend.auth.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Google ID 토큰 확인. 서명·aud·만료 확인은 google-api-client 의 GoogleIdTokenVerifier 가 하므로
 * 여기서는 그 결과를 어떻게 다루는지만 본다. GoogleIdTokenVerifier 는 목으로 바꾼다.
 */
@DisplayName("Google ID 토큰 확인")
class GoogleSocialTokenVerifierTest {

    private final GoogleIdTokenVerifier idTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final GoogleSocialTokenVerifier verifier =
            new GoogleSocialTokenVerifier(Set.of("client-id.apps.googleusercontent.com"), idTokenVerifier);

    private static GoogleIdToken idTokenWithSubject(String subject) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    private void assertReason(SocialTokenVerificationException.Reason reason) {
        assertThatThrownBy(() -> verifier.verify("google-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    @Test
    @DisplayName("확인된 토큰이면 sub 를 돌려준다")
    void returnsSubject() throws Exception {
        when(idTokenVerifier.verify("google-token")).thenReturn(idTokenWithSubject("1084"));

        assertThat(verifier.verify("google-token")).isEqualTo("1084");
    }

    @Test
    @DisplayName("서명, aud, 만료 중 하나라도 맞지 않으면 거절한다")
    void rejectsWhenVerifierReturnsNull() throws Exception {
        when(idTokenVerifier.verify(anyString())).thenReturn(null);

        assertReason(SocialTokenVerificationException.Reason.INVALID);
    }

    @Test
    @DisplayName("JWT 형식이 아니면 거절한다")
    void rejectsMalformedToken() throws Exception {
        when(idTokenVerifier.verify(anyString())).thenThrow(new IllegalArgumentException("malformed"));

        assertReason(SocialTokenVerificationException.Reason.INVALID);
    }

    @Test
    @DisplayName("sub 가 없으면 거절한다")
    void rejectsTokenWithoutSubject() throws Exception {
        when(idTokenVerifier.verify(anyString())).thenReturn(idTokenWithSubject(null));

        assertReason(SocialTokenVerificationException.Reason.INVALID);
    }

    @Test
    @DisplayName("Google 공개키를 받아 오지 못하면 unavailable 로 알린다")
    void reportsUnavailableOnIoFailure() throws Exception {
        when(idTokenVerifier.verify(anyString())).thenThrow(new IOException("timeout"));

        assertReason(SocialTokenVerificationException.Reason.UNAVAILABLE);
    }

    @Test
    @DisplayName("client id 가 비어 있으면 설정되지 않은 것으로 본다")
    void notConfiguredWithoutClientIds() {
        assertThat(new GoogleSocialTokenVerifier("").isConfigured()).isFalse();
        assertThat(new GoogleSocialTokenVerifier(" , ").isConfigured()).isFalse();
        assertThat(new GoogleSocialTokenVerifier("a.apps.googleusercontent.com,b.apps.googleusercontent.com").isConfigured()).isTrue();
    }
}
