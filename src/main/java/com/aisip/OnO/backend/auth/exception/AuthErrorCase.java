package com.aisip.OnO.backend.auth.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCase implements ErrorCase {

    INVALID_REFRESH_TOKEN(400, 1001, "유효하지 않은 리프레시토큰입니다."),

    REFRESH_TOKEN_NOT_FOUND(401, 1002, "리프레시 토큰 정보를 찾을 수 없습니다."),

    INVALID_AUTHORITY(400, 1003, "유효하지 않은 권한입니다."),

    REFRESH_TOKEN_NOT_EQUAL(400, 1004, "리프레시 토큰이 일치하지 않습니다."),

    ACCESS_TOKEN_EXPIRED(401, 1005, "엑세스 토큰이 만료되었습니다."),

    REFRESH_TOKEN_EXPIRED(401, 1006, "리프레시 토큰이 만료되었습니다."),

    AUTHENTICATION_FAILED(401, 1007, "인증이 실패했습니다."),

    ACCESS_DENIED(403, 1008, "접근 권한이 없습니다."),

    INVALID_ACCESS_TOKEN(401, 1009, "유효하지 않은 엑세스 토큰입니다."),

    SOCIAL_TOKEN_REQUIRED(401, 1010, "소셜 로그인 정보가 필요합니다. 앱을 최신 버전으로 업데이트해 주세요."),

    INVALID_SOCIAL_TOKEN(401, 1011, "소셜 로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요."),

    SOCIAL_IDENTIFIER_MISMATCH(401, 1012, "소셜 로그인 계정 정보가 일치하지 않습니다. 다시 로그인해 주세요."),

    SOCIAL_VERIFICATION_UNAVAILABLE(401, 1013, "소셜 로그인 확인이 잠시 어렵습니다. 잠시 후 다시 시도해 주세요.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
