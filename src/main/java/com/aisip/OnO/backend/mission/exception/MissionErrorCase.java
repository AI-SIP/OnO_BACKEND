package com.aisip.OnO.backend.mission.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCase implements ErrorCase {

    MISSION_TYPE_NOT_FOUND(400, 7001, "잘못된 미션 종류입니다."),

    USER_NOT_FOUND(404, 7002, "해당하는 유저가 존재하지 않습니다."),

    // 미션 진행도(mission_progress) 관련. 7001, 7002 에 이어 7010 부터 쓴다.

    /** 남의 진행도를 받으려는 경우에도 이 코드를 준다. 존재 여부를 알려주지 않는다. */
    MISSION_PROGRESS_NOT_FOUND(404, 7010, "미션 진행도를 찾을 수 없습니다."),

    MISSION_NOT_COMPLETED(400, 7011, "아직 완료하지 않은 미션입니다."),

    MISSION_ALREADY_CLAIMED(400, 7012, "이미 보상을 받은 미션입니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
