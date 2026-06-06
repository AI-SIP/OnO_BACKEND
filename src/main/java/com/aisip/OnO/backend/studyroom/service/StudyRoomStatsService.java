package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRoomStatsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EntityManager entityManager;

    public Map<Long, StudyRoomStats> getWeeklyStats(Collection<Long> userIds) {
        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return getStats(userIds, start, end, today);
    }

    public Map<Long, StudyRoomStats> getStats(Collection<Long> userIds, LocalDateTime start, LocalDateTime end, LocalDate today) {
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, Integer> streaks = currentStreaks(ids, today);
        return getStats(ids, start, end, streaks);
    }

    public Map<Long, StudyRoomStats> getStats(Collection<Long> userIds, LocalDateTime start, LocalDateTime end,
                                              Map<Long, Integer> streaks) {
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, Integer> problems = countProblems(userIds, start, end);
        Map<Long, Integer> practices = countPractices(userIds, start, end);
        Map<Long, StudyRoomStats> result = new HashMap<>();
        for (Long userId : ids) {
            result.put(userId, new StudyRoomStats(
                    problems.getOrDefault(userId, 0),
                    practices.getOrDefault(userId, 0),
                    streaks.getOrDefault(userId, 0)
            ));
        }
        return result;
    }

    public Map<Long, Integer> currentStreaks(Collection<Long> userIds, LocalDate today) {
        return currentStreaks(userIds, today, null);
    }

    public Map<Long, Integer> currentStreaks(Collection<Long> userIds, LocalDate today, LocalDate startDate) {
        List<Long> ids = userIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        LocalDateTime startAt = (startDate == null ? LocalDate.of(1970, 1, 1) : startDate).atStartOfDay();
        Map<Long, TreeSet<LocalDate>> studyDates = new HashMap<>();
        addStudyDates(studyDates, findProblemStudyDates(ids, today, startAt));
        addStudyDates(studyDates, findPracticeStudyDates(ids, today, startAt));

        Map<Long, Integer> result = new HashMap<>();
        for (Long userId : ids) {
            result.put(userId, currentStreak(studyDates.getOrDefault(userId, new TreeSet<>()), today));
        }
        return result;
    }

    private Map<Long, Integer> countProblems(Collection<Long> userIds, LocalDateTime start, LocalDateTime end) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select p.userId, count(p.id)
                        from Problem p
                        where p.userId in :userIds and p.createdAt between :start and :end
                        group by p.userId
                        """, Object[].class)
                .setParameter("userIds", userIds)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toCountMap(rows);
    }

    private Map<Long, Integer> countPractices(Collection<Long> userIds, LocalDateTime start, LocalDateTime end) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select ps.userId, count(ps.id)
                        from ProblemSolve ps
                        where ps.userId in :userIds and ps.practicedAt between :start and :end
                        group by ps.userId
                        """, Object[].class)
                .setParameter("userIds", userIds)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return toCountMap(rows);
    }

    private Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], Math.toIntExact((Long) row[1]));
        }
        return result;
    }

    private List<Object[]> findProblemStudyDates(List<Long> userIds, LocalDate today, LocalDateTime startAt) {
        return entityManager.createNativeQuery("""
                        select p.user_id, date(p.created_at)
                        from problem p
                        where p.user_id in (:userIds)
                          and p.created_at >= :startAt
                          and p.created_at < :endExclusive
                          and p.deleted_at is null
                        group by p.user_id, date(p.created_at)
                        """)
                .setParameter("userIds", userIds)
                .setParameter("startAt", startAt)
                .setParameter("endExclusive", today.plusDays(1).atStartOfDay())
                .getResultList();
    }

    private List<Object[]> findPracticeStudyDates(List<Long> userIds, LocalDate today, LocalDateTime startAt) {
        return entityManager.createNativeQuery("""
                        select ps.user_id, date(ps.practiced_at)
                        from problem_solve ps
                        where ps.user_id in (:userIds)
                          and ps.practiced_at >= :startAt
                          and ps.practiced_at < :endExclusive
                          and ps.deleted_at is null
                        group by ps.user_id, date(ps.practiced_at)
                        """)
                .setParameter("userIds", userIds)
                .setParameter("startAt", startAt)
                .setParameter("endExclusive", today.plusDays(1).atStartOfDay())
                .getResultList();
    }

    private void addStudyDates(Map<Long, TreeSet<LocalDate>> studyDates, List<Object[]> rows) {
        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            LocalDate date = toLocalDate(row[1]);
            studyDates.computeIfAbsent(userId, key -> new TreeSet<>()).add(date);
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(value.toString());
    }

    private int currentStreak(TreeSet<LocalDate> dates, LocalDate today) {
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
