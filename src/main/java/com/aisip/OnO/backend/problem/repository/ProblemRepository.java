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
     * 복습 예정일이 지났고, 정답 기록이 {@code masteryThreshold} 개보다 적은 문제.
     *
     * <p>정답 수를 문제 컬럼에 담지 않고 복습 기록에서 바로 센다. 그래야 기록을 고치거나 지워도
     * 바로 반영되고, 규칙을 바꾸기 전에 이미 정답이 쌓인 문제도 따로 옮기지 않고 빠진다.
     */
    @Query("""
            SELECT new com.aisip.OnO.backend.problem.repository.ReviewDueProblemProjection(
                p.id, p.memo, p.reference, p.nextReviewAt, p.reviewInterval, p.consecutiveCorrectCount,
                (SELECT COUNT(ps) FROM ProblemSolve ps
                 WHERE ps.problem = p
                   AND ps.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT))
            FROM Problem p
            WHERE p.userId = :userId AND p.nextReviewAt <= :today
              AND (SELECT COUNT(ps2) FROM ProblemSolve ps2
                   WHERE ps2.problem = p
                     AND ps2.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT) < :masteryThreshold
            ORDER BY p.nextReviewAt ASC
            """)
    List<ReviewDueProblemProjection> findReviewDueProblems(@Param("userId") Long userId,
                                                           @Param("today") LocalDate today,
                                                           @Param("masteryThreshold") long masteryThreshold);

    @Query("""
            SELECT p.userId as userId, COUNT(p) as dueCount
            FROM Problem p
            WHERE p.nextReviewAt <= :today
              AND (SELECT COUNT(ps) FROM ProblemSolve ps
                   WHERE ps.problem = p
                     AND ps.answerStatus = com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT) < :masteryThreshold
            GROUP BY p.userId
            """)
    List<ReviewDueSummary> findReviewDueSummaryByDate(@Param("today") LocalDate today,
                                                      @Param("masteryThreshold") long masteryThreshold);
}
