package com.aisip.OnO.backend.auth.social;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 설정값 문자열을 다루는 작은 도우미. */
final class SocialVerificationSettings {

    private SocialVerificationSettings() {
    }

    /**
     * 쉼표로 구분한 목록을 공백을 걷어 집합으로 만든다. 빈 문자열이면 빈 집합이다.
     *
     * <p>{@code @Value} 로 List 를 바로 받으면 빈 문자열이 [""] 로 들어오는 경우가 있어
     * "설정이 비어 있음"을 놓칠 수 있다. 문자열로 받아 여기서 직접 나눈다.
     */
    static Set<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
