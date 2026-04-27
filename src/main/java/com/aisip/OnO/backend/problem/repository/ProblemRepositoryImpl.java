package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.admin.dto.AdminProblemResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.QProblem;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
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

    @Override
    public List<Problem> findAllByUserId(Long userId) {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(QProblem.problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
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

    @Override
    public List<Problem> findProblemsByFolderWithCursor(Long folderId, Long cursor, int size) {
        var query = queryFactory
                .selectFrom(problem)
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(problem.folder.id.eq(folderId));

        // 커서가 있으면 해당 ID 이후부터 조회
        if (cursor != null) {
            query.where(problem.id.gt(cursor));
        }

        return query
                .orderBy(problem.id.asc())
                .limit(size + 1)  // hasNext 판단을 위해 +1개 조회
                .fetch();
    }

    @Override
    public List<Problem> findProblemsByTagWithCursor(Long tagId, Long userId, Long cursor, int size) {
        var query = queryFactory
                .selectDistinct(problem)
                .from(problem)
                .join(problemTagMapping).on(problemTagMapping.problem.id.eq(problem.id))
                .leftJoin(problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(
                        problemTagMapping.tag.id.eq(tagId),
                        problem.userId.eq(userId)
                );

        if (cursor != null) {
            query.where(problem.id.gt(cursor));
        }

        return query
                .orderBy(problem.id.asc())
                .limit(size + 1)
                .fetch();
    }

    @Override
    public List<Problem> findProblemsByTitleWithCursor(String titleQuery, Long userId, Long cursor, int size) {
        var query = queryFactory
                .selectDistinct(problem)
                .from(problem)
                .leftJoin(problem.folder).fetchJoin()
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(
                        problem.userId.eq(userId),
                        problem.reference.containsIgnoreCase(titleQuery)
                );

        if (cursor != null) {
            query.where(problem.id.gt(cursor));
        }

        return query
                .orderBy(problem.id.asc())
                .limit(size + 1)
                .fetch();
    }
}
