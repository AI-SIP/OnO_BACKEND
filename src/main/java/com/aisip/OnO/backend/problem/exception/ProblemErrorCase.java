package com.aisip.OnO.backend.problem.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemErrorCase implements ErrorCase {

    PROBLEM_NOT_FOUND(400, 4001, "문제를 찾을 수 없습니다."),

    PROBLEM_USER_UNMATCHED(400, 4002, "문제 작성자가 아닙니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
