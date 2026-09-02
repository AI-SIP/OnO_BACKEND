package com.aisip.OnO.backend.feedback.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedbackErrorCase implements ErrorCase {

    FEEDBACK_NOT_FOUND(404, 13001, "피드백을 찾을 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
