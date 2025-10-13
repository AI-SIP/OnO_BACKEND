package com.aisip.OnO.backend.mission.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCase implements ErrorCase {

    MISSION_TYPE_NOT_FOUND(400, 7001, "잘못된 미션 종류입니다."),

    USER_NOT_FOUND(400, 7002, "해당하는 유저가 존재하지 않습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
