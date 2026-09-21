package com.aisip.OnO.backend.problem.repository;

import java.time.LocalDate;

/**
 * 복습 대상 문제 조회 전용 프로젝션.
 *
 * <p>엔티티로 조회하면 {@code Problem.problemAnalysis} 가 mappedBy OneToOne 이라
 * 행마다 존재 여부를 확인하는 select 가 추가로 나간다. 응답에 필요한 값은 스칼라뿐이므로
 * 이 프로젝션으로 단일 쿼리로 읽는다.
 */
public record ReviewDueProblemProjection(
        Long problemId,
        String memo,
        String reference,
        LocalDate nextReviewAt,
        int reviewInterval,
        int consecutiveCorrectCount
) {
}
