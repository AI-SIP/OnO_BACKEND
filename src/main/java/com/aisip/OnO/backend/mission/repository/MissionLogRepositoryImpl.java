package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.aisip.OnO.backend.mission.entity.QMissionLog.missionLog;

public class MissionLogRepositoryImpl implements MissionLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public MissionLogRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public boolean alreadyWriteProblemsTodayMoreThan3(Long userId) {
        Long count = queryFactory
                .select(missionLog.count())
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.PROBLEM_WRITE)
                        .and(missionLog.user.id.eq(userId))
                        .and(missionLog.createdAt.between(getStartOfToday(), getEndOfToday()))
                )
                .fetchOne();

        return count != null && count >= 3;
    }

    @Override
    public boolean alreadyPracticeProblem(Long problemId){

        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.PROBLEM_PRACTICE)
                        .and(missionLog.referenceId.eq(problemId))
                        .and(missionLog.createdAt.between(getStartOfToday(), getEndOfToday()))
                )
                .fetchFirst() != null;
    }

    @Override
    public boolean alreadyPracticeNote(Long practiceNoteId){
        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.NOTE_PRACTICE)
                        .and(missionLog.referenceId.eq(practiceNoteId))
                        .and(missionLog.createdAt.between(getStartOfToday(), getEndOfToday()))
                )
                .fetchFirst() != null;
    }

    @Override
    public boolean alreadyLogin(Long userId){
        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.USER_LOGIN)
                        .and(missionLog.user.id.eq(userId))
                        .and(missionLog.createdAt.between(getStartOfToday(), getEndOfToday()))
                )
                .fetchFirst() != null;
    }

    @Override
    public Long getPointSumToday(Long userId){
        Long result = queryFactory
                .select(missionLog.point.sum())
                .from(missionLog)
                .where(missionLog.createdAt.between(getStartOfToday(), getEndOfToday())
                        .and(missionLog.user.id.eq(userId)))
                .fetchOne();

        return result != null ? result : 0;

    }

    private LocalDateTime getStartOfToday() {
        return LocalDate.now().atStartOfDay();
    }

    private LocalDateTime getEndOfToday() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }
}
