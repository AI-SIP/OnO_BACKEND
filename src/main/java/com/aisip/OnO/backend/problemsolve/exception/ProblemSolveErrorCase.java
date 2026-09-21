package com.aisip.OnO.backend.problemsolve.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemSolveErrorCase implements ErrorCase {

    PROBLEM_SOLVE_NOT_FOUND(404, 4021, "복습 기록을 찾을 수 없습니다."),
    PROBLEM_SOLVE_USER_UNMATCHED(403, 4022, "해당 복습 기록에 대한 권한이 없습니다."),

    /**
     * 필수 입력이 빠진 요청. 예전에는 그대로 NPE / 제약조건 위반으로 터져 500 이 나갔다.
     */
    PROBLEM_SOLVE_INVALID_INPUT(400, 4023, "복습 기록 요청 값이 올바르지 않습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
