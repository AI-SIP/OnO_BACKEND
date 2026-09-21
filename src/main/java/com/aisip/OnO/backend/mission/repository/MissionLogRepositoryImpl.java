package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.user.entity.User;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        return alreadyWriteProblemsTodayMoreThan3(userId, false);
    }

    @Override
    public boolean alreadyWriteProblemsTodayMoreThan3(Long userId, boolean accruedOnly) {
        return countProblemWritesToday(userId, accruedOnly) >= 3;
    }

    @Override
    public long countProblemWritesToday(Long userId) {
        return countProblemWritesToday(userId, false);
    }

    @Override
    public long countProblemWritesToday(Long userId, boolean accruedOnly) {
        Long count = queryFactory
                .select(missionLog.count())
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.PROBLEM_WRITE)
                        .and(missionLog.user.id.eq(userId))
                        .and(createdToday())
                        .and(accrued(accruedOnly))
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    @Override
    public boolean alreadyPracticeProblem(Long problemId){
        return alreadyPracticeProblem(problemId, false);
    }

    @Override
    public boolean alreadyPracticeProblem(Long problemId, boolean accruedOnly){

        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.PROBLEM_PRACTICE)
                        .and(missionLog.referenceId.eq(problemId))
                        .and(createdToday())
                        .and(accrued(accruedOnly))
                )
                .fetchFirst() != null;
    }

    @Override
    public boolean alreadyPracticeNote(Long practiceNoteId){
        return alreadyPracticeNote(practiceNoteId, false);
    }

    @Override
    public boolean alreadyPracticeNote(Long practiceNoteId, boolean accruedOnly){
        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.NOTE_PRACTICE)
                        .and(missionLog.referenceId.eq(practiceNoteId))
                        .and(createdToday())
                        .and(accrued(accruedOnly))
                )
                .fetchFirst() != null;
    }

    @Override
    public boolean alreadyLogin(Long userId){
        return alreadyLogin(userId, false);
    }

    @Override
    public boolean alreadyLogin(Long userId, boolean accruedOnly){
        return queryFactory
                .selectOne()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.USER_LOGIN)
                        .and(missionLog.user.id.eq(userId))
                        .and(createdToday())
                        .and(accrued(accruedOnly))
                )
                .fetchFirst() != null;
    }

    /**
     * "실제로 적립된 행만" 조건. {@code accruedOnly} 가 아니면 조건을 붙이지 않는다.
     *
     * <p>{@code null} 을 돌려주면 QueryDSL 이 그 항을 통째로 빼므로, 기존 판정의 쿼리가 그대로 남는다.
     * 적립된 행은 {@code point} 에 정가가 들어 있고, 적립이 돌지 않은 요청의 행은 0 이다
     * ({@code MissionLog.point}).
     *
     * <p>옛 행은 적립 여부와 무관하게 정가가 들어 있어 전부 "적립된 행"으로 잡힌다.
     * 덜 주는 쪽이 아니라 지금과 같게 두는 쪽이라 안전하다.
     */
    private BooleanExpression accrued(boolean accruedOnly) {
        return accruedOnly ? missionLog.point.gt(0L) : null;
    }

    @Override
    public Long getPointSumToday(Long userId){
        Long result = queryFactory
                .select(missionLog.point.sum())
                .from(missionLog)
                .where(createdToday()
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
                        .and(createdBetweenDates(startDate, endDate))
                )
                .groupBy(createdDate)
                .fetch();

        Map<LocalDate, Long> countByDate = new LinkedHashMap<>();
        for (Tuple row : dailyCounts) {
            LocalDate date = toLocalDate(row.get(createdDate));
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
        return queryFactory
                .select(missionLog.user)
                .distinct()
                .from(missionLog)
                .where(missionLog.missionType.eq(MissionType.USER_LOGIN)
                        .and(createdBetweenDates(date, date))
                )
                .fetch();
    }

    /**
     * "오늘 안에 만들어졌는가" 조건.
     *
     * <p>예전에는 {@code between(오늘 00:00, 오늘 23:59:59.999999999)} 를 썼는데,
     * MySQL DATETIME(6) 은 마이크로초까지만 저장하므로 끝값이 반올림되어 <b>다음 날 00:00:00 이 되고</b>
     * BETWEEN 은 양끝을 포함하므로 자정 정각에 만들어진 기록이 전날에도 오늘로 잡혔다.
     * 그 경우 자정에 로그인한 사용자는 전날 출석이 이미 있는 것으로 판정돼 보상을 잃는다.
     * 반열림 구간 {@code [오늘 00:00, 내일 00:00)} 으로 바꿔 경계를 한 번만 세도록 한다.
     */
    private BooleanExpression createdToday() {
        return createdBetweenDates(LocalDate.now(), LocalDate.now());
    }

    /** {@code [startDate 00:00, endDate+1일 00:00)} 반열림 구간. */
    private BooleanExpression createdBetweenDates(LocalDate startDate, LocalDate endDate) {
        return missionLog.createdAt.goe(startDate.atStartOfDay())
                .and(missionLog.createdAt.lt(endDate.plusDays(1).atStartOfDay()));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return value != null ? LocalDate.parse(value.toString()) : null;
    }
}
