package com.aisip.OnO.backend.auth.social;

import java.util.Arrays;
import java.util.Optional;

/**
 * 소셜 로그인 토큰을 확인할 수 있는 플랫폼.
 *
 * <p>앱은 platform 을 대문자로 보내지만(GOOGLE, APPLE, KAKAO) 예전 데이터와 테스트에는
 * 소문자도 섞여 있어 대소문자를 가리지 않는다.
 */
public enum SocialPlatform {
    GOOGLE,
    APPLE,
    KAKAO;

    public static Optional<SocialPlatform> from(String platform) {
        if (platform == null) {
            return Optional.empty();
        }
        String normalized = platform.strip();
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
