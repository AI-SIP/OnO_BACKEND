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
                .leftJoin(practiceNote.problemPracticeNoteMappingList, problemPracticeNoteMapping).fetchJoin()
                .where(practiceNote.id.eq(practiceNoteId))
                .orderBy(practiceNote.id.asc())
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<PracticeNote> findAllUserPracticeNotesWithDetails(Long userId) {
        return queryFactory
                .selectFrom(practiceNote)
                .leftJoin(practiceNote.problemPracticeNoteMappingList, problemPracticeNoteMapping).fetchJoin()
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

    /**
     * 썸네일 응답에는 문제 매핑이 필요 없다.
     *
     * <p>예전에는 여기서도 {@code problemPracticeNoteMappingList} 를 fetch join 했는데,
     * 컬렉션 fetch join 과 limit 을 같이 쓰면 Hibernate 가 limit 을 SQL 이 아니라 메모리에서 적용한다
     * (HHH90003004). 즉 커서 페이징인데도 사용자의 복습노트와 매핑을 전부 읽어 온 뒤 잘라 냈다.
     * 무한 스크롤 API 에서 페이지 크기와 무관하게 전체를 읽는 셈이라 fetch join 을 걷어냈다.
     */
    @Override
    public List<PracticeNote> findPracticeNotesByUserWithCursor(Long userId, Long cursor, int size) {
        var query = queryFactory
                .selectFrom(practiceNote)
                .where(practiceNote.userId.eq(userId));

        // 커서가 있으면 해당 ID 이후부터 조회
        if (cursor != null) {
            query.where(practiceNote.id.gt(cursor));
        }

        return query
                .orderBy(practiceNote.id.asc())
                .limit(size + 1)  // hasNext 판단을 위해 +1개 조회
                .fetch();
    }
}
