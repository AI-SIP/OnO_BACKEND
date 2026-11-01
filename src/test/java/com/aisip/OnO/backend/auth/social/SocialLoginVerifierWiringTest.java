package com.aisip.OnO.backend.auth.social;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정값만으로 확인기 빈들이 뜨는지 본다. DB 나 외부 서버 없이 이 패키지의 빈만 올린다.
 */
@DisplayName("소셜 로그인 확인기 빈 구성")
class SocialLoginVerifierWiringTest {

    @Configuration
    @Import({
            GoogleSocialTokenVerifier.class,
            AppleSocialTokenVerifier.class,
            KakaoSocialTokenVerifier.class,
            SocialLoginVerifier.class
    })
    static class SocialBeans {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(SocialBeans.class);

    @Test
    @DisplayName("설정이 하나도 없으면 강제하지 않고, 모든 플랫폼이 설정되지 않은 상태로 뜬다")
    void startsWithDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(ReflectionTestUtils.getField(context.getBean(SocialLoginVerifier.class), "enforce"))
                    .isEqualTo(false);
            assertThat(context.getBeansOfType(SocialTokenVerifier.class).values())
                    .hasSize(3)
                    .noneMatch(SocialTokenVerifier::isConfigured);
        });
    }

    @Test
    @DisplayName("client id 와 app id 를 주면 플랫폼별로 설정된 상태가 된다")
    void readsConfiguredIds() {
        runner.withPropertyValues(
                        "auth.social-verification.enforce=true",
                        "auth.social-verification.google.client-ids=a.apps.googleusercontent.com, b.apps.googleusercontent.com",
                        "auth.social-verification.apple.client-ids=com.example.app",
                        "auth.social-verification.kakao.app-ids=123456"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(ReflectionTestUtils.getField(context.getBean(SocialLoginVerifier.class), "enforce"))
                            .isEqualTo(true);
                    assertThat(context.getBeansOfType(SocialTokenVerifier.class).values())
                            .allMatch(SocialTokenVerifier::isConfigured);
                });
    }
}
