package com.aisip.OnO.backend.fcm.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FcmErrorCase implements ErrorCase {

    FCM_TOKEN_NOT_FOUND(400, 7001, "Fcm Token을 찾을 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
