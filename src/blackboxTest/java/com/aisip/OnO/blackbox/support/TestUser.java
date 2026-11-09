package com.aisip.OnO.blackbox.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * API 로 가입한 사용자 하나. DB 에 직접 넣지 않고 앱이 실제로 내준 토큰을 쓴다.
 */
public record TestUser(long userId, String accessToken, String refreshToken) {

    /** 게스트로 가입한다. 시나리오마다 새로 만들어서 다른 시나리오의 데이터와 섞이지 않게 한다. */
    public static TestUser guest() {
        JsonNode tokens = Api.anonymous().post("/api/auth/signup/guest").ok();
        return fromTokens(tokens);
    }

    public static TestUser fromTokens(JsonNode tokens) {
        String access = tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();
        if (access.isBlank() || refresh.isBlank()) {
            throw new IllegalStateException("가입 응답에 토큰이 없습니다: " + tokens);
        }
        return new TestUser(subjectOf(access), access, refresh);
    }

    /**
     * userId 는 토큰의 sub 에서 읽는다. GET /api/users 로 얻으면 출석 미션 기록과 접속 시각 갱신이 같이 돌아서
     * 미션 시나리오의 출발 상태가 달라진다.
     */
    private static long subjectOf(String accessToken) {
        String jwt = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        return Api.parse(payload).path("sub").asLong();
    }

    public Api api() {
        return Api.anonymous().withToken(accessToken);
    }

    /** 옛 버전 앱처럼 보내야 하는 요청이 있을 때. 미션과 XP 적립이 버전으로 갈린다. */
    public Api api(String appVersion) {
        return api().withHeader("X-App-Version", appVersion);
    }
}
