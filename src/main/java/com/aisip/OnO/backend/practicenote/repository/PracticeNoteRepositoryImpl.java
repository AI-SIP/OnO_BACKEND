package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static com.aisip.OnO.backend.practicenote.entity.QPracticeNote.practiceNote;
import static com.aisip.OnO.backend.practicenote.entity.QProblemPracticeNoteMapping.problemPracticeNoteMapping;

public class PracticeNoteRepositoryImpl implements PracticeNoteRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PracticeNoteRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public boolean checkProblemAlreadyMatchingWithPractice(Long practiceNoteId, Long problemId) {
        return queryFactory
                .selectOne()
                .from(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.practiceNote.id.eq(practiceNoteId)
                        .and(problemPracticeNoteMapping.problem.id.eq(problemId)))
                .fetchFirst() != null;
    }

    @Override
    public Optional<PracticeNote> findPracticeNoteWithDetails(Long practiceNoteId) {
        PracticeNote result = queryFactory
                .selectFrom(practiceNote)
                .join(practiceNote.problemPracticeNoteMappingList, problemPracticeNoteMapping).fetchJoin()
                .where(practiceNote.id.eq(practiceNoteId))
                .orderBy(practiceNote.id.asc())
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<PracticeNote> findAllUserPracticeNotesWithDetails(Long userId) {
        return queryFactory
                .selectFrom(practiceNote)
                .join(practiceNote.problemPracticeNoteMappingList, problemPracticeNoteMapping).fetchJoin()
                .where(practiceNote.userId.eq(userId))
                .orderBy(practiceNote.id.asc())
                .fetch();
    }

    @Override
    public List<Long> findProblemIdListByPracticeNoteId(Long practiceNoteId) {
        return queryFactory
                .select(problemPracticeNoteMapping.problem.id)
                .distinct()  // 중복 제거
                .from(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.practiceNote.id.eq(practiceNoteId))
                .orderBy(problemPracticeNoteMapping.problem.id.asc())
                .fetch();
    }

    @Override
    public void deleteProblemFromPractice(Long practiceNoteId, Long problemId) {
        queryFactory
                .delete(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.practiceNote.id.eq(practiceNoteId)
                        .and(problemPracticeNoteMapping.problem.id.eq(problemId)))
                .execute();
    }

    @Override
    public void deleteProblemFromAllPractice(Long problemId) {
        queryFactory
                .delete(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.problem.id.eq(problemId))
                .execute();
    }

    @Override
    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        // 1. 삭제할 문제 ID 리스트에 해당하는 모든 매핑 삭제 (벌크 삭제)
        queryFactory
                .delete(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.problem.id.in(deleteProblemIdList))
                .execute();
    }
}
