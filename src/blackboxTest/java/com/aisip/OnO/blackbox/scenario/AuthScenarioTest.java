package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxStack;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.SocialLogin;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입, 소셜 로그인, 토큰 갱신, 로그아웃. 앱이 토큰을 처음 받고 계속 쓰는 경로다.
 */
class AuthScenarioTest extends BlackboxTest {

    @BeforeAll
    static void registerJwks() {
        BlackboxStack.get();
        SocialLogin.registerJwks();
    }

    @Test
    void 게스트로_가입한_토큰으로_내_루트_폴더를_조회한다() {
        TestUser user = TestUser.guest();

        var root = user.api().get("/api/folders/root").ok();

        assertThat(root.path("folderId").asLong()).isPositive();
    }

    @Test
    void 토큰_없이_보호된_API_를_부르면_401_1007() {
        Api.anonymous().get("/api/problems/problemCount")
                .expectError(401, ErrorCodes.AUTHENTICATION_FAILED);
    }

    @Test
    void 서명이_틀린_토큰은_거절한다() {
        String forged = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiYXV0aG9yaXR5IjoiUk9MRV9NRU1CRVIifQ."
                + "c2lnbmF0dXJlLWZyb20tbm93aGVyZQ";

        var response = Api.anonymous().withToken(forged).get("/api/problems/problemCount");

        response.expectStatus(401);
        assertThat(response.errorCode()).isIn(ErrorCodes.INVALID_ACCESS_TOKEN, ErrorCodes.AUTHENTICATION_FAILED);
    }

    @Nested
    @DisplayName("토큰 갱신")
    class TokenRefresh {

        @Test
        void refresh_token_으로_새_토큰을_받고_새_access_token_이_동작한다() {
            TestUser user = TestUser.guest();

            JsonNode renewed = Api.anonymous()
                    .post("/api/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                    .ok();

            assertThat(renewed.path("refreshToken").asText()).isNotEqualTo(user.refreshToken());
            Api.anonymous().withToken(renewed.path("accessToken").asText())
                    .get("/api/problems/problemCount").ok();
        }

        @Test
        void 한_번_쓴_refresh_token_은_다시_쓸_수_없다() {
            TestUser user = TestUser.guest();
            Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", user.refreshToken())).ok();

            Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                    .expectError(401, ErrorCodes.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void refresh_token_자리에_access_token_을_보내면_거절한다() {
            TestUser user = TestUser.guest();

            Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", user.accessToken().substring(7)))
                    .expectClientError();
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        void 로그아웃하면_그_access_token_과_refresh_token_을_더_쓸_수_없다() {
            TestUser user = TestUser.guest();

            user.api().post("/api/auth/logout", Map.of("refreshToken", user.refreshToken())).ok();

            user.api().get("/api/problems/problemCount").expectError(401, ErrorCodes.INVALID_ACCESS_TOKEN);
            Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", user.refreshToken()))
                    .expectError(401, ErrorCodes.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void 남의_refresh_token_을_보내도_그_사람의_세션은_끊기지_않는다() {
            TestUser me = TestUser.guest();
            TestUser other = TestUser.guest();

            me.api().post("/api/auth/logout", Map.of("refreshToken", other.refreshToken())).ok();

            Api.anonymous().post("/api/auth/refresh", Map.of("refreshToken", other.refreshToken())).ok();
        }

        @Test
        void 토큰_없이_로그아웃하면_401() {
            Api.anonymous().post("/api/auth/logout", Map.of()).expectStatus(401);
        }
    }

    @Nested
    @DisplayName("소셜 로그인")
    class SocialSignUp {

        @Test
        void 카카오_토큰으로_가입하고_같은_계정으로_다시_로그인하면_같은_사용자다() {
            long kakaoId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);

            JsonNode first = signUp("KAKAO", String.valueOf(kakaoId),
                    SocialLogin.kakaoToken(kakaoId, Long.parseLong(BlackboxStack.KAKAO_APP_ID))).ok();
            JsonNode second = signUp("KAKAO", String.valueOf(kakaoId),
                    SocialLogin.kakaoToken(kakaoId, Long.parseLong(BlackboxStack.KAKAO_APP_ID))).ok();

            assertThat(TestUser.fromTokens(first).userId()).isEqualTo(TestUser.fromTokens(second).userId());
        }

        @Test
        void 다른_앱에_발급된_카카오_토큰은_거절한다() {
            long kakaoId = 777_000_001L;

            signUp("KAKAO", String.valueOf(kakaoId), SocialLogin.kakaoToken(kakaoId, 9_999_999L))
                    .expectError(401, ErrorCodes.INVALID_SOCIAL_TOKEN);
        }

        @Test
        void 카카오가_모르는_토큰이면_가입되지_않는다() {
            // 등록하지 않은 토큰이라 WireMock 이 404 를 준다. 실제 카카오의 만료 토큰 응답(401)과 같은 쪽으로 처리돼야 한다.
            var response = signUp("KAKAO", "123", "kakao-unknown-token");

            response.expectStatus(401);
            assertThat(response.errorCode()).isIn(ErrorCodes.INVALID_SOCIAL_TOKEN, ErrorCodes.SOCIAL_VERIFICATION_UNAVAILABLE);
        }

        @Test
        void 구글_id_token_으로_가입하면_회원_토큰이_나온다() {
            String subject = "google-" + UUID.randomUUID();

            JsonNode tokens = signUp("GOOGLE", subject,
                    SocialLogin.googleIdToken(subject, BlackboxStack.GOOGLE_CLIENT_ID)).ok();

            TestUser user = TestUser.fromTokens(tokens);
            user.api().get("/api/folders/root").ok();
        }

        @Test
        void 다른_사람의_구글_토큰으로_내_identifier_에_가입할_수_없다() {
            String victim = "google-" + UUID.randomUUID();

            signUp("GOOGLE", victim, SocialLogin.googleIdToken("google-attacker", BlackboxStack.GOOGLE_CLIENT_ID))
                    .expectError(401, ErrorCodes.SOCIAL_IDENTIFIER_MISMATCH);
        }

        @Test
        void 우리_앱이_아닌_audience_의_구글_토큰은_거절한다() {
            String subject = "google-" + UUID.randomUUID();

            signUp("GOOGLE", subject, SocialLogin.googleIdToken(subject, "someone-elses-client"))
                    .expectError(401, ErrorCodes.INVALID_SOCIAL_TOKEN);
        }

        @Test
        void 모르는_키로_서명한_구글_토큰은_거절한다() {
            String subject = "google-" + UUID.randomUUID();

            signUp("GOOGLE", subject, SocialLogin.googleIdTokenSignedByStranger(subject))
                    .expectClientError();
        }

        @Test
        void 애플_identity_token_으로_가입한다() {
            String subject = "apple-" + UUID.randomUUID();

            JsonNode tokens = signUp("APPLE", subject, SocialLogin.appleIdentityToken(subject)).ok();

            TestUser.fromTokens(tokens).api().get("/api/folders/root").ok();
        }

        @Test
        void 소셜_토큰_없이_회원가입하면_거절한다() {
            signUp("GOOGLE", "google-no-token", null)
                    .expectError(401, ErrorCodes.SOCIAL_TOKEN_REQUIRED);
        }

        private com.aisip.OnO.blackbox.support.ApiResponse signUp(String platform, String identifier, String token) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", identifier + "@blackbox.test");
            body.put("name", "블랙박스");
            body.put("identifier", identifier);
            body.put("platform", platform);
            body.put("socialToken", token);
            return Api.anonymous().post("/api/auth/signup/member", body);
        }
    }
}
