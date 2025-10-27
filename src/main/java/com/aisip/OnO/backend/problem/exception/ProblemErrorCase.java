package com.aisip.OnO.backend.problem.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemErrorCase implements ErrorCase {

    PROBLEM_NOT_FOUND(400, 4001, "문제를 찾을 수 없습니다."),

    PROBLEM_USER_UNMATCHED(400, 4002, "문제 작성자가 아닙니다."),

    PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED(400, 4003, "이미 오늘의 복습을 완료한 문제입니다."),

    PROBLEM_ANALYSIS_NOT_FOUND(404, 4004, "문제 분석 결과를 찾을 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
