package com.aisip.OnO.backend.learningcalendar.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LearningCalendarErrorCase implements ErrorCase {

    CALENDAR_RECORD_NOT_FOUND(404, 12001, "해당 날짜 학습 기록이 없습니다."),
    INVALID_DATE_FORMAT(400, 12002, "날짜 형식이 올바르지 않습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
