package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.problem.dto.ReviewDueResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.QProblem;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aisip.OnO.backend.folder.entity.QFolder.folder;
import static com.aisip.OnO.backend.practicenote.entity.QPracticeNote.practiceNote;
import static com.aisip.OnO.backend.practicenote.entity.QProblemPracticeNoteMapping.problemPracticeNoteMapping;
import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.problem.entity.QProblemAnalysis.problemAnalysis;
import static com.aisip.OnO.backend.problem.entity.QProblemImageData.problemImageData;
import static com.aisip.OnO.backend.tag.entity.QProblemTagMapping.problemTagMapping;

public class ProblemRepositoryImpl implements ProblemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ProblemRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Problem> findProblemWithImageData(Long problemId) {
        Problem problem = queryFactory
                .selectFrom(QProblem.problem)
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(QProblem.problem.problemImageDataList, problemImageData).fetchJoin()
                .where(QProblem.problem.id.eq(problemId))
                .fetchOne();

        return Optional.ofNullable(problem);
    }

    /**
     * problemAnalysis 는 mappedBy OneToOne 이라 프록시로 미룰 수도, 배치 페치로 묶을 수도 없다.
     * fetch join 하지 않으면 Hibernate 가 "행마다 분석이 있는지" 확인하는 쿼리를 한 번씩 더 던져
     * 문제 개수에 비례해 쿼리가 늘어난다(N+1). 응답 DTO 가 이 값을 항상 읽으므로 함께 가져온다.
     */
    @Override
    public List<Problem> findAllByUserId(Long userId) {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .leftJoin(problem.problemAnalysis, problemAnalysis).fetchJoin()
                .where(problem.userId.eq(userId))
                .orderBy(problem.id.asc())
                .fetch();
    }

    @Override
    public List<Problem> findAllByFolderId(Long folderId) {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .leftJoin(problem.problemAnalysis, problemAnalysis).fetchJoin()
                .where(problem.folder.id.eq(folderId))
                .orderBy(problem.id.asc())
                .fetch();
    }

    @Override
    public List<Problem> findAll() {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .orderBy(problem.id.asc())
                .fetch();
    }

    @Override
    public List<ReviewDueResponseDto.ReviewDueProblemDto> findReviewDueProblemDtos(Long userId, LocalDate today) {
        return queryFactory
                .select(Projections.constructor(
                        ReviewDueResponseDto.ReviewDueProblemDto.class,
                        problem.id,
                        problem.memo,
                        problem.reference,
                        problem.nextReviewAt,
                        problem.reviewInterval,
                        problem.consecutiveCorrectCount
                ))
                .from(problem)
                .where(problem.userId.eq(userId)
                        .and(problem.nextReviewAt.loe(today)))
                .orderBy(problem.nextReviewAt.asc())
                .fetch();
    }

    @Override
    public Page<AdminProblemResponseDto> findAdminProblems(Pageable pageable) {
        List<AdminProblemResponseDto> content = queryFactory
                .select(Projections.constructor(
                        AdminProblemResponseDto.class,
                        problem.id,
                        folder.id,
                        problem.memo,
                        problem.reference,
                        problemAnalysis.status.stringValue(),
                        problem.solvedAt,
                        problem.createdAt
                ))
                .from(problem)
                .leftJoin(problem.folder, folder)
                .leftJoin(problem.problemAnalysis, problemAnalysis)
                .orderBy(problem.createdAt.desc(), problem.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(problem.count())
                .from(problem)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Map<LocalDate, Long> countDailyProblems(LocalDate startDate, LocalDate endDate) {
        DateExpression<Date> createdDate = Expressions.dateTemplate(
                Date.class,
                "date({0})",
                problem.createdAt
        );

        List<Tuple> rows = queryFactory
                .select(createdDate, problem.count())
                .from(problem)
                .where(problem.createdAt.between(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)))
                .groupBy(createdDate)
                .fetch();

        Map<LocalDate, Long> countsByDate = new LinkedHashMap<>();
        rows.forEach(row -> {
            Date date = row.get(createdDate);
            if (date != null) {
                countsByDate.put(date.toLocalDate(), row.get(problem.count()));
            }
        });

        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            result.put(date, countsByDate.getOrDefault(date, 0L));
        }

        return result;
    }

    @Override
    public long countProblemAnalysesForActiveProblems() {
        Long count = queryFactory
                .select(problemAnalysis.count())
                .from(problemAnalysis)
                .join(problemAnalysis.problem, problem)
                .where(problem.deletedAt.isNull())
                .fetchOne();

        return count != null ? count : 0L;
    }

    @Override
    public Map<AnalysisStatus, Long> countProblemAnalysesByStatusForActiveProblems() {
        return countProblemAnalysesByStatusForActiveProblems(null, null);
    }

    @Override
    public Map<AnalysisStatus, Long> countProblemAnalysesByStatusForActiveProblems(LocalDate startDate, LocalDate endDate) {
        var query = queryFactory
                .select(problemAnalysis.status, problemAnalysis.count())
                .from(problemAnalysis)
                .join(problemAnalysis.problem, problem)
                .where(problem.deletedAt.isNull());

        if (startDate != null && endDate != null) {
            query.where(problemAnalysis.updatedAt.between(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)));
        }

        List<Tuple> rows = query
                .groupBy(problemAnalysis.status)
                .fetch();

        Map<AnalysisStatus, Long> result = new EnumMap<>(AnalysisStatus.class);
        rows.forEach(row -> result.put(row.get(problemAnalysis.status), row.get(problemAnalysis.count())));

        return result;
    }

    @Override
    public List<Problem> findAllProblemsByPracticeId(Long practiceId) {
        return queryFactory
                .select(problem)
                .from(problem)
                .join(problemPracticeNoteMapping).on(problem.id.eq(problemPracticeNoteMapping.problem.id))
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(practiceNote.id.eq(practiceId))
                .orderBy(problem.id.asc())
                .fetch();
    }

    /**
     * 커서 페이징 + 컬렉션 fetch join 을 한 쿼리에 같이 쓰면 조인으로 행이 뻥튀기돼
     * Hibernate 가 SQL 에 LIMIT 을 걸지 못하고 조건에 맞는 행을 전부 메모리로 읽은 뒤
     * 자바에서 잘라낸다 (경고 HHH90003004). size 를 보내도 폴더의 모든 문제가 올라왔다.
     *
     * 그래서 1단계에서 id 만 limit 으로 뽑고, 2단계에서 그 id 로 fetch join 한다.
     */
    private BooleanExpression cursorAfter(Long cursor) {
        return cursor == null ? null : problem.id.gt(cursor);
    }

    private List<Problem> fetchProblemsWithImages(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectDistinct(problem)
                .from(problem)
                .leftJoin(problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(problem.id.in(problemIds))
                .orderBy(problem.id.asc())
                .fetch();
    }

    @Override
    public List<Problem> findProblemsByFolderWithCursor(Long folderId, Long cursor, int size) {
        List<Long> problemIds = queryFactory
                .select(problem.id)
                .from(problem)
                .where(
                        problem.folder.id.eq(folderId),
                        cursorAfter(cursor)
                )
                .orderBy(problem.id.asc())
                .limit(size + 1)  // hasNext 판단을 위해 +1개 조회
                .fetch();

        return fetchProblemsWithImages(problemIds);
    }

    @Override
    public List<Problem> findProblemsByTagWithCursor(Long tagId, Long userId, Long cursor, int size) {
        List<Long> problemIds = queryFactory
                .selectDistinct(problem.id)
                .from(problem)
                .join(problemTagMapping).on(problemTagMapping.problem.id.eq(problem.id))
                .where(
                        problemTagMapping.tag.id.eq(tagId),
                        problem.userId.eq(userId),
                        cursorAfter(cursor)
                )
                .orderBy(problem.id.asc())
                .limit(size + 1)
                .fetch();

        return fetchProblemsWithImages(problemIds);
    }

    @Override
    public List<Problem> findProblemsByTitleWithCursor(String titleQuery, Long userId, Long cursor, int size) {
        List<Long> problemIds = queryFactory
                .select(problem.id)
                .from(problem)
                .where(
                        problem.userId.eq(userId),
                        problem.reference.containsIgnoreCase(titleQuery),
                        cursorAfter(cursor)
                )
                .orderBy(problem.id.asc())
                .limit(size + 1)
                .fetch();

        return fetchProblemsWithImages(problemIds);
    }
}
