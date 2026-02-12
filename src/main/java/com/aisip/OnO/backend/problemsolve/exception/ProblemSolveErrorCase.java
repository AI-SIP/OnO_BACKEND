package com.aisip.OnO.backend.problemsolve.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProblemSolveErrorCase implements ErrorCase {

    PRACTICE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, 5001, "연습 기록을 찾을 수 없습니다."),
    PRACTICE_RECORD_USER_UNMATCHED(HttpStatus.FORBIDDEN, 5002, "해당 연습 기록에 대한 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer code;
    private final String message;
}