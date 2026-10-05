package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long>, ProblemRepositoryCustom {

    Long countByUserId(Long userId);

    /**
     * 복습 예정일이 지났고, 마지막으로 틀린 뒤에 정답을 남긴 날이 {@code masteryThreshold} 일보다
     * 적은 문제. 추천에서 빠진 뒤에 틀리면 다시 센다.
     *
     * <p>같은 날 여러 번 맞힌 것은 하루로 센다. 몰아 맞힌 것을 간격 복습으로 보지 않는
     * {@link com.aisip.OnO.backend.problem.service.ReviewIntervalCalculator#replay} 와 같은 기준이다.
     *
     * <p>정답 수를 문제 컬럼에 담지 않고 복습 기록에서 바로 센다. 그래야 기록을 고치거나 지워도
     * 바로 반영되고, 규칙을 바꾸기 전에 이미 정답이 쌓인 문제도 따로 옮기지 않고 빠진다.
     */
    @Query("""
            SELECT new com.aisip.OnO.backend.problem.repository.ReviewDueProblemProjection(
                p.id, p.memo, p.reference, p.nextReviewAt, p.reviewInterval, p.consecutiveCorrectCount,
                (SELECT COUNT(DISTINCT cast(ps.practicedAt as LocalDate)) FROM ProblemSolve ps
                 WHERE ps.problem = p
                   AND ps.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT
                   AND NOT EXISTS (SELECT 1 FROM ProblemSolve wps
                                   WHERE wps.problem = p
                                     AND wps.answerStatus IN (com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.WRONG,
                                                           com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.PARTIAL)
                                     AND (wps.practicedAt > ps.practicedAt
                                          OR (wps.practicedAt = ps.practicedAt AND wps.id > ps.id)))))
            FROM Problem p
            WHERE p.userId = :userId AND p.nextReviewAt <= :today
              AND (SELECT COUNT(DISTINCT cast(ps2.practicedAt as LocalDate)) FROM ProblemSolve ps2
                   WHERE ps2.problem = p
                     AND ps2.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT
                     AND NOT EXISTS (SELECT 1 FROM ProblemSolve wps2
                                     WHERE wps2.problem = p
                                       AND wps2.answerStatus IN (com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.WRONG,
                                                             com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.PARTIAL)
                                       AND (wps2.practicedAt > ps2.practicedAt
                                            OR (wps2.practicedAt = ps2.practicedAt AND wps2.id > ps2.id)))) < :masteryThreshold
            ORDER BY p.nextReviewAt ASC
            """)
    List<ReviewDueProblemProjection> findReviewDueProblems(@Param("userId") Long userId,
                                                           @Param("today") LocalDate today,
                                                           @Param("masteryThreshold") long masteryThreshold);

    @Query("""
            SELECT p.userId as userId, COUNT(p) as dueCount
            FROM Problem p
            WHERE p.nextReviewAt <= :today
              AND (SELECT COUNT(DISTINCT cast(ps.practicedAt as LocalDate)) FROM ProblemSolve ps
                   WHERE ps.problem = p
                     AND ps.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT
                     AND NOT EXISTS (SELECT 1 FROM ProblemSolve wps
                                     WHERE wps.problem = p
                                       AND wps.answerStatus IN (com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.WRONG,
                                                             com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.PARTIAL)
                                       AND (wps.practicedAt > ps.practicedAt
                                            OR (wps.practicedAt = ps.practicedAt AND wps.id > ps.id)))) < :masteryThreshold
            GROUP BY p.userId
            """)
    List<ReviewDueSummary> findReviewDueSummaryByDate(@Param("today") LocalDate today,
                                                      @Param("masteryThreshold") long masteryThreshold);
}
