package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.user.entity.User;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @Override
    public Map<LocalDate, Long> getDailyActiveUsersCount(int days) {
        LocalDate today = LocalDate.now();
        return getDailyActiveUsersCount(today.minusDays(days - 1L), today);
    }

    @Override
    public Map<LocalDate, Long> getDailyActiveUsersCount(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        DateExpression<LocalDate> createdDate = Expressions.dateTemplate(
                LocalDate.class,
                "date({0})",
                missionLog.createdAt
        );
        NumberExpression<Long> activeUserCount = missionLog.user.id.countDistinct();

        java.util.List<Tuple> dailyCounts = queryFactory
                .select(createdDate, activeUserCount)
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.USER_LOGIN)
                        .and(missionLog.createdAt.between(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)))
                )
                .groupBy(createdDate)
                .fetch();

        Map<LocalDate, Long> countByDate = new LinkedHashMap<>();
        for (Tuple row : dailyCounts) {
            LocalDate date = row.get(createdDate);
            Long count = row.get(activeUserCount);
            if (date != null) {
                countByDate.put(date, count != null ? count : 0L);
            }
        }

        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            result.put(date, countByDate.getOrDefault(date, 0L));
        }

        return result;
    }

    @Override
    public java.util.List<User> getActiveUsersByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        return queryFactory
                .select(missionLog.user)
                .distinct()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.USER_LOGIN)
                        .and(missionLog.createdAt.between(startOfDay, endOfDay))
                )
                .fetch();
    }

    private LocalDateTime getStartOfToday() {
        return LocalDate.now().atStartOfDay();
    }

    private LocalDateTime getEndOfToday() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }
}
