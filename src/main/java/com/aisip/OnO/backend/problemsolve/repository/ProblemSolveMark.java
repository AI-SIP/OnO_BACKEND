package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;

import java.time.LocalDateTime;

/**
 * 복습 기록 한 건에서 판정에 필요한 값만 뽑은 것.
 *
 * <p>엔티티를 그대로 읽지 않는다. {@code reflection} 과 {@code improvements} 가 TEXT 라
 * 복습 기록이 수천 건인 사용자의 것을 통째로 올리면 본문만 메가바이트 단위가 된다.
 * 회고는 "비어 있지 않은가" 만 보면 되므로 길이만 가져온다.
 *
 * @param reflectionLength 공백을 걷어낸 회고의 길이. 회고가 없으면 {@code null} 이다.
 */
public record ProblemSolveMark(
        Long problemId,
        LocalDateTime practicedAt,
        AnswerStatus answerStatus,
        Integer reflectionLength
) {

    /** 회고를 남긴 기록인지. 공백만 적은 것은 남긴 것으로 치지 않는다. */
    public boolean hasReflection() {
        return reflectionLength != null && reflectionLength > 0;
    }
}
