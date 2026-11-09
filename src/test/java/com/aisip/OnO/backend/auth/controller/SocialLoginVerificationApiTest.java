package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.auth.social.SocialLoginVerifier;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 로그인 토큰 확인이 가입·로그인 API 에 물려 있는지, 거절이 어떤 응답이 되는지 본다.
 *
 * <p>test 프로필에는 플랫폼별 client id 가 없어서 확인기가 플랫폼 서버를 부르지 않는다.
 * 토큰이 맞거나 다른 사용자의 것인 경우는 {@code SocialLoginVerifierTest} 에서 가짜 확인기로 본다.
 *
 * <p>enforce 를 바꾸려고 새 컨텍스트를 띄우지 않도록, 같은 빈의 값을 테스트 동안만 바꾸고 되돌린다.
 */
@DisplayName("소셜 로그인 토큰 확인 API")
class SocialLoginVerificationApiTest extends IntegrationTestSupport {

    @Autowired
    private SocialLoginVerifier socialLoginVerifier;

    @Autowired
    private UserRepository userRepository;

    private Object verifierTarget;
    private boolean originalEnforce;

    @BeforeEach
    void rememberEnforce() {
        verifierTarget = AopTestUtils.getUltimateTargetObject(socialLoginVerifier);
        originalEnforce = (boolean) ReflectionTestUtils.getField(verifierTarget, "enforce");
    }

    @AfterEach
    void restoreEnforce() {
        ReflectionTestUtils.setField(verifierTarget, "enforce", originalEnforce);
    }

    private void enforce(boolean value) {
        ReflectionTestUtils.setField(verifierTarget, "enforce", value);
    }

    private ResultActions signUp(String platform, String identifier, String socialToken) throws Exception {
        return mockMvc.perform(post("/api/auth/signup/member")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UserRegisterDto.builder()
                        .identifier(identifier)
                        .platform(platform)
                        .name("소셜사용자")
                        .email(identifier + "@test.ono")
                        .socialToken(socialToken)
                        .build())));
    }

    @Test
    @DisplayName("기본값은 강제하지 않는다")
    void notEnforcedByDefault() {
        assertThat(originalEnforce).isFalse();
    }

    @Nested
    @DisplayName("강제하지 않을 때")
    class NotEnforced {

        @Test
        @DisplayName("토큰 없이 보내는 기존 앱도 로그인된다")
        void legacyRequestWithoutTokenSucceeds() throws Exception {
            signUp("GOOGLE", "google-sub-legacy", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

            assertThat(userRepository.findByIdentifier("google-sub-legacy")).isPresent();
        }

        @Test
        @DisplayName("확인 설정이 없는 플랫폼의 토큰은 확인하지 않고 로그인시킨다")
        void tokenForUnconfiguredPlatformSucceeds() throws Exception {
            signUp("KAKAO", "1234567890", "kakao-token")
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("확인할 수 없는 플랫폼의 토큰은 401 + 1011 로 거절하고 계정을 만들지 않는다")
        void tokenForUnsupportedPlatformRejected() throws Exception {
            signUp("NAVER", "naver-sub-1", "some-token")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_SOCIAL_TOKEN.getErrorCode()));

            assertThat(userRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("강제할 때")
    class Enforced {

        @BeforeEach
        void turnOn() {
            enforce(true);
        }

        @Test
        @DisplayName("토큰이 없으면 401 + 1010 으로 거절하고 계정을 만들지 않는다")
        void requestWithoutTokenRejected() throws Exception {
            signUp("APPLE", "apple-sub-legacy", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.SOCIAL_TOKEN_REQUIRED.getErrorCode()));

            assertThat(userRepository.count()).isZero();
        }

        @Test
        @DisplayName("확인 설정이 없으면 401 + 1013 으로 거절한다")
        void unconfiguredPlatformRejected() throws Exception {
            signUp("GOOGLE", "google-sub-1", "google-token")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.SOCIAL_VERIFICATION_UNAVAILABLE.getErrorCode()));

            assertThat(userRepository.count()).isZero();
        }

        @Test
        @DisplayName("게스트 가입은 소셜 토큰과 상관없이 된다")
        void guestSignUpUnaffected() throws Exception {
            mockMvc.perform(post("/api/auth/signup/guest"))
                    .andExpect(status().isOk());
        }
    }
}
