package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long>, ProblemRepositoryCustom {

    Long countByUserId(Long userId);

    @Query("""
            SELECT new com.aisip.OnO.backend.problem.repository.ReviewDueProblemProjection(
                p.id, p.memo, p.reference, p.nextReviewAt, p.reviewInterval, p.consecutiveCorrectCount)
            FROM Problem p
            WHERE p.userId = :userId AND p.nextReviewAt <= :today
            ORDER BY p.nextReviewAt ASC
            """)
    List<ReviewDueProblemProjection> findReviewDueProblems(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query("SELECT p.userId as userId, COUNT(p) as dueCount FROM Problem p WHERE p.nextReviewAt <= :today GROUP BY p.userId")
    List<ReviewDueSummary> findReviewDueSummaryByDate(@Param("today") LocalDate today);
}
