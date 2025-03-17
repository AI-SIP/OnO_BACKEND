package com.aisip.OnO.backend.auth.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCase implements ErrorCase {

    INVALID_REFRESH_TOKEN(400, 1001, "유효하지 않은 리프레시토큰입니다."),

    REFRESH_TOKEN_NOT_FOUND(404, 1002, "리프레시 토큰 정보를 찾을 수 없습니다."),

    INVALID_AUTHORITY(400, 1003, "유효하지 않은 권한입니다."),

    REFRESH_TOKEN_NOT_EQUAL(400, 1004, "리프레시 토큰이 일치하지 않습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
