package com.aisip.OnO.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 앱이 보내오는 버전 문자열 파싱.
 *
 * <p>이 값은 앱이 채우는 헤더라 서버가 무엇이 올지 정할 수 없다. <b>어떤 입력에도 예외를 던지지 않고
 * 빈 값으로 떨어지는 것</b>이 이 클래스의 계약이다. 여기서 예외가 새면 XP 적립 경로 한가운데서
 * 요청이 500 으로 죽는다.
 */
@DisplayName("앱 버전 파싱")
class AppVersionTest {

    @Nested
    @DisplayName("읽을 수 있는 값")
    class Parsable {

        @Test
        @DisplayName("빌드 번호는 버리고 semver 만 읽는다")
        void dropsBuildNumber() {
            // 프론트가 pubspec.yaml 의 version 을 그대로 보낸다(AI-SIP/OnO_FRONT#214).
            assertThat(AppVersion.parse("4.0.0+70"))
                    .contains(new AppVersion(4, 0, 0));
        }

        @Test
        @DisplayName("빌드 번호가 없어도 읽는다")
        void parsesPlainSemver() {
            assertThat(AppVersion.parse("4.2.13"))
                    .contains(new AppVersion(4, 2, 13));
        }

        @Test
        @DisplayName("프리릴리즈 꼬리도 떼고 읽는다")
        void dropsPreReleaseSuffix() {
            // 내부 배포 빌드가 통째로 구버전으로 떨어지지 않게 앞자리만 읽는다.
            assertThat(AppVersion.parse("4.1.0-beta.2+81"))
                    .contains(new AppVersion(4, 1, 0));
        }

        @Test
        @DisplayName("앞뒤 공백은 무시한다")
        void trimsWhitespace() {
            assertThat(AppVersion.parse("  4.0.0+70  "))
                    .contains(new AppVersion(4, 0, 0));
        }
    }

    @Nested
    @DisplayName("읽을 수 없는 값")
    class NotParsable {

        @ParameterizedTest(name = "\"{0}\"")
        @NullSource
        @ValueSource(strings = {
                "",
                "   ",
                "abc",
                "4",          // 자리가 모자란다
                "4.0",        // 자리가 모자란다
                "4.0.0.1",    // 자리가 넘친다
                "4.0.x",
                "-1.0.0",
                "v4.0.0",
                "+70",
                "99999999999.0.0"  // int 를 넘긴다
        })
        @DisplayName("예외 없이 빈 값으로 떨어진다")
        void returnsEmptyWithoutThrowing(String raw) {
            assertThat(AppVersion.parse(raw)).isEmpty();
        }
    }

    @Nested
    @DisplayName("버전 비교")
    class Comparison {

        @Test
        @DisplayName("같은 버전은 기준을 만족한다")
        void equalSatisfiesThreshold() {
            assertThat(version("4.0.0").isAtLeast(version("4.0.0"))).isTrue();
        }

        @Test
        @DisplayName("자리별로 앞자리가 우선한다")
        void comparesMajorMinorPatchInOrder() {
            assertThat(version("4.0.0").isAtLeast(version("3.99.99"))).isTrue();
            assertThat(version("3.99.99").isAtLeast(version("4.0.0"))).isFalse();
            assertThat(version("4.1.0").isAtLeast(version("4.0.99"))).isTrue();
            assertThat(version("4.0.1").isAtLeast(version("4.0.0"))).isTrue();
            assertThat(version("4.0.0").isAtLeast(version("4.0.1"))).isFalse();
        }

        @Test
        @DisplayName("빌드 번호는 비교에 끼어들지 않는다")
        void buildNumberDoesNotAffectComparison() {
            // 같은 스토어 버전의 다른 빌드가 서로 다르게 판정되면 설정값을 제출마다 고쳐야 한다.
            assertThat(version("4.0.0+70")).isEqualTo(version("4.0.0+999"));
        }

        private AppVersion version(String raw) {
            return AppVersion.parse(raw).orElseThrow(
                    () -> new IllegalArgumentException("테스트 입력이 잘못됐다: " + raw));
        }
    }

    @Test
    @DisplayName("반환형이 Optional 이라 호출부가 실패를 무시할 수 없다")
    void returnsOptional() {
        Optional<AppVersion> parsed = AppVersion.parse("4.0.0");

        assertThat(parsed).isPresent();
    }
}
