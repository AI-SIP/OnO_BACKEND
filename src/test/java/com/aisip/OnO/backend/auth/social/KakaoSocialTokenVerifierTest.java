package com.aisip.OnO.backend.auth.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Kakao 액세스 토큰 확인. 토큰 정보 조회 응답은 MockRestServiceServer 가 대신한다.
 */
@DisplayName("Kakao 액세스 토큰 확인")
class KakaoSocialTokenVerifierTest {

    private static final String TOKEN_INFO_URL = "https://kakao.test/v1/user/access_token_info";
    private static final String APP_ID = "123456";

    private MockRestServiceServer server;
    private KakaoSocialTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new KakaoSocialTokenVerifier(Set.of(APP_ID), TOKEN_INFO_URL, builder.build());
    }

    private void respondWith(String body) {
        server.expect(requestTo(TOKEN_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("우리 앱에 발급된 토큰이면 회원번호를 돌려준다")
    void returnsUserIdForOwnApp() {
        respondWith("{\"id\":987654321,\"expires_in\":7199,\"app_id\":" + APP_ID + ",\"extra\":true}");

        assertThat(verifier.verify("kakao-token")).isEqualTo("987654321");
        server.verify();
    }

    @Test
    @DisplayName("다른 앱에 발급된 토큰은 거절한다")
    void rejectsTokenOfOtherApp() {
        respondWith("{\"id\":987654321,\"expires_in\":7199,\"app_id\":999}");

        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    @DisplayName("회원번호가 없는 응답은 거절한다")
    void rejectsResponseWithoutId() {
        respondWith("{\"app_id\":" + APP_ID + "}");

        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    @DisplayName("Kakao 가 401 을 주면 거절한다")
    void rejectsWhenKakaoSaysUnauthorized() {
        server.expect(requestTo(TOKEN_INFO_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}"));

        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    @DisplayName("Kakao 가 5xx 를 주면 unavailable 로 알린다")
    void reportsUnavailableOnServerError() {
        server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    @DisplayName("Kakao 가 429 를 주면 토큰 탓이 아니므로 unavailable 로 알린다")
    void reportsUnavailableOnTooManyRequests() {
        server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> verifier.verify("kakao-token"))
                .isInstanceOfSatisfying(SocialTokenVerificationException.class,
                        e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    @DisplayName("app id 가 비어 있으면 설정되지 않은 것으로 본다")
    void notConfiguredWithoutAppIds() {
        assertThat(new KakaoSocialTokenVerifier(Set.of(), TOKEN_INFO_URL, RestClient.create()).isConfigured()).isFalse();
    }
}
