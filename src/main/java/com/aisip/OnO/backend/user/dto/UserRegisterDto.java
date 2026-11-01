package com.aisip.OnO.backend.user.dto;

import lombok.Builder;

/**
 * 소셜 로그인 가입·로그인 요청과 프로필 수정 요청이 함께 쓴다.
 *
 * @param socialToken 소셜 로그인 토큰. Google 은 ID 토큰, Apple 은 identityToken, Kakao 는 액세스 토큰이다.
 *                    4.1.1 까지의 앱은 보내지 않아 null 일 수 있다. 가입·로그인에서만 읽는다.
 */
@Builder
public record UserRegisterDto(
    String email,
    String name,
    String identifier,
    String platform,

    String password,

    String socialToken
) {

    /** socialToken 이 없던 때의 생성자. 서버 안에서 사용자를 만드는 경로(게스트, 관리자)가 쓴다. */
    public UserRegisterDto(String email, String name, String identifier, String platform, String password) {
        this(email, name, identifier, platform, password, null);
    }

    /** 토큰과 비밀번호가 로그에 그대로 찍히지 않게 가린다. */
    @Override
    public String toString() {
        return "UserRegisterDto[email=" + email
                + ", name=" + name
                + ", platform=" + platform
                + ", password=" + (password == null ? "null" : "***")
                + ", socialToken=" + (socialToken == null ? "null" : "***")
                + "]";
    }
}
