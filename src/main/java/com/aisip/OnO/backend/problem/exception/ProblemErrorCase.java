package com.aisip.OnO.backend.problem.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemErrorCase implements ErrorCase {

    PROBLEM_NOT_FOUND(404, 4001, "문제를 찾을 수 없습니다."),

    PROBLEM_USER_UNMATCHED(403, 4002, "문제 작성자가 아닙니다."),

    PROBLEM_SOLVE_IMAGE_ALREADY_REGISTERED(400, 4003, "이미 오늘의 복습을 완료한 문제입니다."),

    PROBLEM_ANALYSIS_NOT_FOUND(404, 4004, "문제 분석 결과를 찾을 수 없습니다."),

    ANALYSIS_RATE_LIMIT_EXCEEDED(429, 4005, "AI 분석 일일 요청 횟수를 초과했습니다."),

    PROBLEM_MEMO_TOO_LONG(400, 4006, "메모는 1000자를 넘을 수 없습니다."),

    PROBLEM_REFERENCE_TOO_LONG(400, 4007, "출처는 255자를 넘을 수 없습니다."),

    PROBLEM_FOLDER_ID_REQUIRED(400, 4008, "폴더 ID는 필수입니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
