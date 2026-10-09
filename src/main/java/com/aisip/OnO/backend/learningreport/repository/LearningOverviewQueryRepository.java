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

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.aisip.OnO.backend.folder.entity.QFolder.folder;
import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.problemsolve.entity.QProblemSolve.problemSolve;

/**
 * 학습 보고서 개요 집계 쿼리.
 *
 * <p>풀이 기록은 항상 {@code problem} 과 조인한다. 문제를 지워도 풀이 기록은 남아 있어서 조인 없이 세면
 * 지운 문제의 기록이 숫자에 들어간다. 조인하면 {@code Problem} 의 {@code @SQLRestriction} 이 붙어
 * 지운 문제가 빠진다.
 */
@Repository
public class LearningOverviewQueryRepository {

    private final JPAQueryFactory queryFactory;

    public LearningOverviewQueryRepository(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    /**
     * 날짜와 채점 결과별 풀이 기록 수. 요약, 비교 기간, 막대 그래프를 이 한 번의 결과로 나눠 담는다.
     */
    public List<DailyAnswerCount> findDailyAnswerCounts(Long userId, LocalDateTime start, LocalDateTime end) {
        DateExpression<Date> practicedDate = Expressions.dateTemplate(
                Date.class, "DATE({0})", problemSolve.practicedAt
        );

        List<Tuple> rows = queryFactory
                .select(practicedDate, problemSolve.answerStatus, problemSolve.count())
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .where(problemSolve.userId.eq(userId)
                        .and(problem.userId.eq(userId))
                        .and(problemSolve.practicedAt.between(start, end)))
                .groupBy(practicedDate, problemSolve.answerStatus)
                .fetch();

        return rows.stream()
                .map(row -> new DailyAnswerCount(
                        row.get(practicedDate).toLocalDate(),
                        row.get(problemSolve.answerStatus),
                        row.get(problemSolve.count())
                ))
                .toList();
    }

    public long countProblems(Long userId) {
        Long count = queryFactory
                .select(problem.count())
                .from(problem)
                .where(problem.userId.eq(userId))
                .fetchOne();
        return count == null ? 0L : count;
    }

    /**
     * 졸업 판정에 쓰는 풀이 기록 전체. 문제마다 시간순으로 돌려야 해서 시각, id 순으로 읽는다.
     * 시각이 같은 기록이 있어도 판정이 호출마다 달라지지 않게 id 로 한 번 더 정렬한다.
     */
    public List<SolveMarkRow> findSolveMarks(Long userId) {
        return queryFactory
                .select(problem.id, problemSolve.practicedAt, problemSolve.answerStatus)
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .where(problemSolve.userId.eq(userId)
                        .and(problem.userId.eq(userId)))
                .orderBy(problemSolve.practicedAt.asc(), problemSolve.id.asc())
                .fetch()
                .stream()
                .map(row -> new SolveMarkRow(
                        row.get(problem.id),
                        row.get(problemSolve.practicedAt),
                        row.get(problemSolve.answerStatus)
                ))
                .toList();
    }

    /**
     * 문제의 지금 폴더(직접 소속)별 채점 결과 수. 지운 폴더는 {@code Folder} 의 {@code @SQLRestriction} 으로 빠진다.
     *
     * @param start {@code null} 이면 처음부터 센다
     */
    public List<FolderAnswerCount> findFolderAnswerCounts(Long userId, LocalDateTime start, LocalDateTime end) {
        NumberExpression<Long> correct = countOf(AnswerStatus.CORRECT);
        NumberExpression<Long> partial = countOf(AnswerStatus.PARTIAL);
        NumberExpression<Long> wrong = countOf(AnswerStatus.WRONG);

        List<Tuple> rows = queryFactory
                .select(folder.id, folder.name, correct, partial, wrong, problemSolve.count())
                .from(problemSolve)
                .join(problemSolve.problem, problem)
                .join(problem.folder, folder)
                .where(problemSolve.userId.eq(userId)
                        .and(problem.userId.eq(userId))
                        .and(start == null
                                ? problemSolve.practicedAt.loe(end)
                                : problemSolve.practicedAt.between(start, end)))
                .groupBy(folder.id, folder.name)
                .fetch();

        return rows.stream()
                .map(row -> new FolderAnswerCount(
                        row.get(folder.id),
                        row.get(folder.name),
                        nullToZero(row.get(correct)),
                        nullToZero(row.get(partial)),
                        nullToZero(row.get(wrong)),
                        nullToZero(row.get(problemSolve.count()))
                ))
                .toList();
    }

    private NumberExpression<Long> countOf(AnswerStatus status) {
        return new CaseBuilder()
                .when(problemSolve.answerStatus.eq(status)).then(1L)
                .otherwise(0L)
                .sum();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    public record DailyAnswerCount(LocalDate practicedDate, AnswerStatus answerStatus, long count) {
    }

    public record SolveMarkRow(Long problemId, LocalDateTime practicedAt, AnswerStatus answerStatus) {
    }

    public record FolderAnswerCount(
            Long folderId, String name, long correctCount, long partialCount, long wrongCount, long totalCount
    ) {
    }
}
