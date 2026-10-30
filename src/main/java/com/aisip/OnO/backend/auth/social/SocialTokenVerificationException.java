package com.aisip.OnO.backend.auth.social;

/**
 * 플랫폼별 확인기가 던지는 실패. 응답 에러 코드로 바꾸는 일은 {@link SocialLoginVerifier} 가 한다.
 *
 * <p>토큰 자체가 맞지 않는 경우(INVALID)와 플랫폼 서버에 닿지 못한 경우(UNAVAILABLE)를 나눈다.
 * 뒤엣것은 사용자 탓이 아니라서, 강제 전에는 로그인을 막지 않는다.
 */
public class SocialTokenVerificationException extends RuntimeException {

    public enum Reason {
        INVALID,
        UNAVAILABLE
    }

    private final Reason reason;

    private SocialTokenVerificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public static SocialTokenVerificationException invalid(String message) {
        return new SocialTokenVerificationException(Reason.INVALID, message, null);
    }

    public static SocialTokenVerificationException invalid(String message, Throwable cause) {
        return new SocialTokenVerificationException(Reason.INVALID, message, cause);
    }

    public static SocialTokenVerificationException unavailable(String message, Throwable cause) {
        return new SocialTokenVerificationException(Reason.UNAVAILABLE, message, cause);
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isUnavailable() {
        return reason == Reason.UNAVAILABLE;
    }
}
