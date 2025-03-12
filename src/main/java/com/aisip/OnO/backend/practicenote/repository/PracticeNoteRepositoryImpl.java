package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public PracticeNote findPracticeNoteWithDetails(Long practiceNoteId) {
        return queryFactory
                .select(practiceNote)
                .from(practiceNote)
                .join(problemPracticeNoteMapping).on(practiceNote.id.eq(problemPracticeNoteMapping.practiceNote.id))
                .join(problemPracticeNoteMapping.practiceNote, practiceNote)
                .where(practiceNote.id.eq(practiceNoteId))
                .fetchOne();
    }

    @Override
    public Set<Long> findProblemIdListByPracticeNoteId(Long practiceNoteId) {
        return new HashSet<>(queryFactory
                .select(problemPracticeNoteMapping.problem.id)
                .from(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.practiceNote.id.eq(practiceNoteId))
                .fetch());
    }

    @Override
    public List<PracticeNote> findPracticesByProblem(Long problemId) {

        return queryFactory
                .select(practiceNote)
                .from(practiceNote)
                .join(practiceNote.problemPracticeNoteMappingList, problemPracticeNoteMapping) // 중간 매핑 테이블 조인
                .where(problemPracticeNoteMapping.problem.id.eq(problemId))
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
    public void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList) {
        // 1. 삭제할 문제 ID 리스트에 해당하는 모든 매핑 삭제 (벌크 삭제)
        queryFactory
                .delete(problemPracticeNoteMapping)
                .where(problemPracticeNoteMapping.problem.id.in(deleteProblemIdList))
                .execute();
    }
}
