package com.aisip.OnO.backend.auth.social;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 소셜 로그인 토큰 확인 단계를 강제 여부(enforce)별로 확인한다.
 * 플랫폼 서버 호출은 가짜 확인기로 바꿔 끼운다.
 */
@DisplayName("소셜 로그인 토큰 확인")
class SocialLoginVerifierTest {

    private static final String IDENTIFIER = "kakao-user-1";
    private static final String TOKEN = "token-value";

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    private SocialLoginVerifier verifierWith(boolean enforce, SocialTokenVerifier platformVerifier) {
        return new SocialLoginVerifier(List.of(platformVerifier), meterRegistry, enforce);
    }

    private static UserRegisterDto request(String platform, String identifier, String socialToken) {
        return UserRegisterDto.builder()
                .platform(platform)
                .identifier(identifier)
                .name("사용자")
                .socialToken(socialToken)
                .build();
    }

    private double count(String platform, String result) {
        Counter counter = meterRegistry.find(SocialLoginVerifier.METRIC_NAME)
                .tag("platform", platform)
                .tag("result", result)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static ApplicationException rejected(Runnable call) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(ApplicationException.class);
        return (ApplicationException) thrown;
    }

    /** 받은 토큰을 그대로 함수에 넘기는 가짜 확인기. */
    private static SocialTokenVerifier fakeKakao(boolean configured, Function<String, String> behavior) {
        return new SocialTokenVerifier() {
            @Override
            public SocialPlatform platform() {
                return SocialPlatform.KAKAO;
            }

            @Override
            public boolean isConfigured() {
                return configured;
            }

            @Override
            public String verify(String token) {
                return behavior.apply(token);
            }
        };
    }

    private static SocialTokenVerifier returning(String subject) {
        return fakeKakao(true, token -> subject);
    }

    private static SocialTokenVerifier throwing(SocialTokenVerificationException exception) {
        return fakeKakao(true, token -> {
            throw exception;
        });
    }

    @Nested
    @DisplayName("강제하지 않을 때(enforce=false)")
    class NotEnforced {

        @Test
        @DisplayName("토큰이 없으면 예전처럼 통과시키고 missing 으로 센다")
        void missingTokenPasses() {
            SocialLoginVerifier verifier = verifierWith(false, returning(IDENTIFIER));

            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, null))).doesNotThrowAnyException();
            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, "  "))).doesNotThrowAnyException();

            assertThat(count("KAKAO", "missing")).isEqualTo(2);
        }

        @Test
        @DisplayName("토큰이 가리키는 사용자가 identifier 와 같으면 통과한다")
        void validTokenPasses() {
            SocialLoginVerifier verifier = verifierWith(false, fakeKakao(true, token -> {
                assertThat(token).isEqualTo(TOKEN);
                return IDENTIFIER;
            }));

            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN))).doesNotThrowAnyException();
            assertThat(count("KAKAO", "verified")).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰이 맞지 않으면 1011 로 거절한다")
        void invalidTokenRejected() {
            SocialLoginVerifier verifier = verifierWith(false, throwing(SocialTokenVerificationException.invalid("rejected")));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.INVALID_SOCIAL_TOKEN);
            assertThat(count("KAKAO", "invalid")).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰이 다른 사용자의 것이면 1012 로 거절한다")
        void mismatchedIdentifierRejected() {
            SocialLoginVerifier verifier = verifierWith(false, returning("kakao-user-2"));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.SOCIAL_IDENTIFIER_MISMATCH);
            assertThat(count("KAKAO", "mismatch")).isEqualTo(1);
        }

        @Test
        @DisplayName("플랫폼 서버에 닿지 못하면 통과시키고 unavailable 로 센다")
        void unavailablePasses() {
            SocialLoginVerifier verifier = verifierWith(false,
                    throwing(SocialTokenVerificationException.unavailable("timeout", null)));

            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN))).doesNotThrowAnyException();
            assertThat(count("KAKAO", "unavailable")).isEqualTo(1);
        }

        @Test
        @DisplayName("확인 설정이 비어 있으면 확인하지 않고 통과시킨다")
        void unconfiguredPasses() {
            SocialLoginVerifier verifier = verifierWith(false, fakeKakao(false, token -> {
                throw new AssertionError("설정이 없으면 플랫폼 서버를 부르지 않는다");
            }));

            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN))).doesNotThrowAnyException();
            assertThat(count("KAKAO", "unconfigured")).isEqualTo(1);
        }

        @Test
        @DisplayName("확인할 수 없는 플랫폼에 토큰이 오면 1011 로 거절한다")
        void unsupportedPlatformRejected() {
            SocialLoginVerifier verifier = verifierWith(false, returning(IDENTIFIER));

            ApplicationException e = rejected(() -> verifier.verify(request("NAVER", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.INVALID_SOCIAL_TOKEN);
            assertThat(count("UNKNOWN", "unsupported")).isEqualTo(1);
        }

        @Test
        @DisplayName("platform 은 대소문자를 가리지 않는다")
        void platformIsCaseInsensitive() {
            SocialLoginVerifier verifier = verifierWith(false, returning(IDENTIFIER));

            assertThatCode(() -> verifier.verify(request("kakao", IDENTIFIER, TOKEN))).doesNotThrowAnyException();
            assertThat(count("KAKAO", "verified")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("강제할 때(enforce=true)")
    class Enforced {

        @Test
        @DisplayName("토큰이 없으면 1010 으로 거절한다")
        void missingTokenRejected() {
            SocialLoginVerifier verifier = verifierWith(true, returning(IDENTIFIER));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, null)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.SOCIAL_TOKEN_REQUIRED);
            assertThat(count("KAKAO", "missing")).isEqualTo(1);
        }

        @Test
        @DisplayName("platform 이 비어 있어도 토큰이 없으면 거절한다")
        void missingTokenWithoutPlatformRejected() {
            SocialLoginVerifier verifier = verifierWith(true, returning(IDENTIFIER));

            assertThatThrownBy(() -> verifier.verify(request(null, IDENTIFIER, null)))
                    .isInstanceOf(ApplicationException.class);
            assertThat(count("UNKNOWN", "missing")).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰이 가리키는 사용자가 identifier 와 같으면 통과한다")
        void validTokenPasses() {
            SocialLoginVerifier verifier = verifierWith(true, returning(IDENTIFIER));

            assertThatCode(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN))).doesNotThrowAnyException();
            assertThat(count("KAKAO", "verified")).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰이 맞지 않으면 1011 로 거절한다")
        void invalidTokenRejected() {
            SocialLoginVerifier verifier = verifierWith(true, throwing(SocialTokenVerificationException.invalid("rejected")));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.INVALID_SOCIAL_TOKEN);
        }

        @Test
        @DisplayName("토큰이 다른 사용자의 것이면 1012 로 거절한다")
        void mismatchedIdentifierRejected() {
            SocialLoginVerifier verifier = verifierWith(true, returning("kakao-user-2"));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.SOCIAL_IDENTIFIER_MISMATCH);
        }

        @Test
        @DisplayName("플랫폼 서버에 닿지 못하면 1013 으로 거절한다")
        void unavailableRejected() {
            SocialLoginVerifier verifier = verifierWith(true,
                    throwing(SocialTokenVerificationException.unavailable("timeout", null)));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.SOCIAL_VERIFICATION_UNAVAILABLE);
        }

        @Test
        @DisplayName("확인 설정이 비어 있으면 1013 으로 거절한다")
        void unconfiguredRejected() {
            SocialLoginVerifier verifier = verifierWith(true, fakeKakao(false, token -> IDENTIFIER));

            ApplicationException e = rejected(() -> verifier.verify(request("KAKAO", IDENTIFIER, TOKEN)));

            assertThat(e.getErrorCase()).isEqualTo(AuthErrorCase.SOCIAL_VERIFICATION_UNAVAILABLE);
        }
    }
}
