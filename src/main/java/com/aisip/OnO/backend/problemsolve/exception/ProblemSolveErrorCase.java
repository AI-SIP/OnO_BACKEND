package com.aisip.OnO.backend.problemsolve.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemSolveErrorCase implements ErrorCase {

    PROBLEM_SOLVE_NOT_FOUND(400, 4021, "복습 기록을 찾을 수 없습니다."),
    PROBLEM_SOLVE_USER_UNMATCHED(400, 4022, "해당 복습 기록에 대한 권한이 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}