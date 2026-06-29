package com.aisip.OnO.backend.learningcalendar.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.problemsolve.entity.QProblemSolve.problemSolve;

@Repository
public class LearningCalendarQueryRepository {

    private final JPAQueryFactory queryFactory;

    public LearningCalendarQueryRepository(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    public List<DailyReviewStat> findDailyReviewStats(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<Date> practicedDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problemSolve.practicedAt
        );
        NumberExpression<Integer> totalSeconds = problemSolve.timeSpentSeconds.sum();

        List<Tuple> rows = queryFactory
                .select(practicedDate, problemSolve.count(), totalSeconds)
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .groupBy(practicedDate)
                .orderBy(practicedDate.asc())
                .fetch();

        return rows.stream()
                .map(row -> new DailyReviewStat(
                        row.get(practicedDate).toLocalDate(),
                        defaultLong(row.get(problemSolve.count())),
                        secondsToMinutes(row.get(totalSeconds))
                ))
                .toList();
    }

    public List<DailyNoteWriteStat> findDailyNoteWriteStats(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<Date> createdDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problem.createdAt
        );

        List<Tuple> rows = queryFactory
                .select(createdDate, problem.count())
                .from(problem)
                .where(problem.userId.eq(userId)
                        .and(problem.createdAt.between(start, end)))
                .groupBy(createdDate)
                .orderBy(createdDate.asc())
                .fetch();

        return rows.stream()
                .map(row -> new DailyNoteWriteStat(
                        row.get(createdDate).toLocalDate(),
                        defaultLong(row.get(problem.count()))
                ))
                .toList();
    }

    public List<ReviewItem> findReviewItems(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<Date> practicedDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problemSolve.practicedAt
        );

        List<Tuple> rows = queryFactory
                .select(practicedDate, problem.id, problem.reference, problem.memo, problemSolve.practicedAt)
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .orderBy(practicedDate.asc(), problemSolve.practicedAt.desc())
                .fetch();

        return rows.stream()
                .map(row -> new ReviewItem(
                        row.get(practicedDate).toLocalDate(),
                        itemTitle(row.get(problem.id), row.get(problem.reference), row.get(problem.memo)),
                        row.get(problemSolve.practicedAt)
                ))
                .toList();
    }

    public List<LocalDate> findDistinctReviewDatesTotal(Long userId) {
        DateExpression<Date> practicedDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problemSolve.practicedAt
        );

        return queryFactory
                .select(practicedDate)
                .distinct()
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId))
                .orderBy(practicedDate.asc())
                .fetch()
                .stream()
                .map(Date::toLocalDate)
                .toList();
    }

    public List<LocalDate> findDistinctNoteWriteDatesTotal(Long userId) {
        DateExpression<Date> createdDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problem.createdAt
        );

        return queryFactory
                .select(createdDate)
                .distinct()
                .from(problem)
                .where(problem.userId.eq(userId))
                .orderBy(createdDate.asc())
                .fetch()
                .stream()
                .map(Date::toLocalDate)
                .toList();
    }

    public boolean existsStudyRecord(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        Integer reviewExists = queryFactory
                .selectOne()
                .from(problemSolve)
                .where(problemSolve.userId.eq(userId)
                        .and(problemSolve.practicedAt.between(start, end)))
                .fetchFirst();
        if (reviewExists != null) {
            return true;
        }
        Integer noteWriteExists = queryFactory
                .selectOne()
                .from(problem)
                .where(problem.userId.eq(userId)
                        .and(problem.createdAt.between(start, end)))
                .fetchFirst();
        return noteWriteExists != null;
    }

    private int secondsToMinutes(Integer seconds) {
        return seconds == null ? 0 : seconds / 60;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    public record DailyReviewStat(LocalDate date, long reviewCount, int studyMinutes) {
    }

    public record DailyNoteWriteStat(LocalDate date, long noteWriteCount) {
    }

    public record ReviewItem(LocalDate date, String title, LocalDateTime practicedAt) {
    }

    private String itemTitle(Long problemId, String reference, String memo) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        if (memo != null && !memo.isBlank()) {
            return memo;
        }
        return "문제 " + problemId;
    }
}
