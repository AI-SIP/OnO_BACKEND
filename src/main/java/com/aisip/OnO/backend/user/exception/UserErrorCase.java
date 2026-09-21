package com.aisip.OnO.backend.user.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCase implements ErrorCase {

    USER_NOT_FOUND(404, 3001, "사용자를 찾을 수 없습니다."),

    /**
     * 소셜 로그인 요청에 identifier 가 없거나, 암호화 후 저장 한계를 넘는 경우.
     * identifier 없이 가입시키면 다음 로그인에서 같은 계정을 찾지 못해 계정이 계속 늘어난다.
     */
    INVALID_USER_IDENTIFIER(400, 3002, "유효하지 않은 사용자 식별자입니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
