package com.aisip.OnO.backend.problem.repository.problem;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;

import static com.aisip.OnO.backend.practicenote.entity.QPracticeNote.practiceNote;
import static com.aisip.OnO.backend.practicenote.entity.QProblemPracticeNoteMapping.problemPracticeNoteMapping;
import static com.aisip.OnO.backend.problem.entity.QProblem.problem;
import static com.aisip.OnO.backend.problem.entity.QProblemImageData.problemImageData;

public class ProblemRepositoryImpl implements ProblemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ProblemRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Problem> findAllByUserId(Long userId) {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(problem.userId.eq(userId))
                .fetch();
    }

    @Override
    public List<Problem> findAllByFolderId(Long folderId) {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(problem.folder.id.eq(folderId))
                .fetch();
    }

    @Override
    public List<Problem> findAll() {
        return queryFactory
                .selectFrom(problem)
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .fetch();
    }

    @Override
    public List<Problem> findAllProblemsByPracticeId(Long practiceId) {
        return queryFactory
                .select(problem)
                .from(problem)
                .join(problemPracticeNoteMapping).on(problem.id.eq(problemPracticeNoteMapping.problem.id))
                .leftJoin(problem.problemImageDataList, problemImageData).fetchJoin()
                .where(practiceNote.id.eq(practiceId))
                .fetch();
    }
}
