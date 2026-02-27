package com.aisip.OnO.backend.learningreport.repository;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.problem.entity.QProblemAnalysis.problemAnalysis;
import static com.aisip.OnO.backend.problemsolve.entity.QProblemSolve.problemSolve;

@Repository
public class LearningReportQueryRepository {

    private final JPAQueryFactory queryFactory;

    public LearningReportQueryRepository(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    public Long countReviewsInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .select(problemSolve.count())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .fetchOne();
    }

    public Long countReviewsTotal(Long userId) {
        return queryFactory
                .select(problemSolve.count())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId))
                .fetchOne();
    }

    public Double averageAccuracyInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .select(accuracyScore().avg())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .fetchOne();
    }

    public Double averageAccuracyTotal(Long userId) {
        return queryFactory
                .select(accuracyScore().avg())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId))
                .fetchOne();
    }

    public Double averageStudyTimeInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .select(problemSolve.timeSpentSeconds.avg())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end))
                        .and(problemSolve.timeSpentSeconds.isNotNull())
                        .and(problemSolve.timeSpentSeconds.gt(0)))
                .fetchOne();
    }

    public Double averageStudyTimeTotal(Long userId) {
        return queryFactory
                .select(problemSolve.timeSpentSeconds.avg())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.timeSpentSeconds.isNotNull())
                        .and(problemSolve.timeSpentSeconds.gt(0)))
                .fetchOne();
    }

    public List<DailySolveCount> findDailySolveCounts(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<LocalDate> practicedDate = Expressions.dateTemplate(
                LocalDate.class, "DATE({0})", problemSolve.practicedAt
        );

        List<Tuple> rows = queryFactory
                .select(practicedDate, problemSolve.count())
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .groupBy(practicedDate)
                .orderBy(practicedDate.asc())
                .fetch();

        return rows.stream()
                .map(row -> new DailySolveCount(
                        row.get(practicedDate),
                        row.get(problemSolve.count())
                ))
                .toList();
    }

    public List<LocalDate> findDistinctPracticeDatesInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<LocalDate> practicedDate = Expressions.dateTemplate(
                LocalDate.class, "DATE({0})", problemSolve.practicedAt
        );

        return queryFactory
                .select(practicedDate)
                .distinct()
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .orderBy(practicedDate.asc())
                .fetch();
    }

    public List<LocalDate> findDistinctPracticeDatesTotal(Long userId) {
        DateExpression<LocalDate> practicedDate = Expressions.dateTemplate(
                LocalDate.class, "DATE({0})", problemSolve.practicedAt
        );

        return queryFactory
                .select(practicedDate)
                .distinct()
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId))
                .orderBy(practicedDate.asc())
                .fetch();
    }

    public List<WeakAreaCount> findTopWeakAreasInPeriod(Long userId, LocalDateTime start, LocalDateTime end, int limit) {
        List<Tuple> rows = queryFactory
                .select(problemAnalysis.problemType, problemSolve.count())
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .join(problem.problemAnalysis, problemAnalysis)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.answerStatus.eq(AnswerStatus.WRONG))
                        .and(problemSolve.practicedAt.between(start, end))
                        .and(problemAnalysis.problemType.isNotNull()))
                .groupBy(problemAnalysis.problemType)
                .orderBy(problemSolve.count().desc())
                .limit(limit)
                .fetch();

        return rows.stream()
                .map(row -> new WeakAreaCount(
                        row.get(problemAnalysis.problemType),
                        row.get(problemSolve.count())
                ))
                .toList();
    }

    public List<WeakAreaCount> findTopWeakAreasTotal(Long userId, int limit) {
        List<Tuple> rows = queryFactory
                .select(problemAnalysis.problemType, problemSolve.count())
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .join(problem.problemAnalysis, problemAnalysis)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.answerStatus.eq(AnswerStatus.WRONG))
                        .and(problemAnalysis.problemType.isNotNull()))
                .groupBy(problemAnalysis.problemType)
                .orderBy(problemSolve.count().desc())
                .limit(limit)
                .fetch();

        return rows.stream()
                .map(row -> new WeakAreaCount(
                        row.get(problemAnalysis.problemType),
                        row.get(problemSolve.count())
                ))
                .toList();
    }

    private NumberExpression<Double> accuracyScore() {
        return new CaseBuilder()
                .when(problemSolve.answerStatus.eq(AnswerStatus.CORRECT)).then(1.0)
                .when(problemSolve.answerStatus.eq(AnswerStatus.PARTIAL)).then(0.5)
                .otherwise(0.0);
    }

    public record DailySolveCount(LocalDate practicedDate, Long solveCount) {
    }

    public record WeakAreaCount(String topic, Long wrongCount) {
    }
}
