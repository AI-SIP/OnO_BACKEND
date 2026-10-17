package com.aisip.OnO.backend.admin.service;

import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.AnalysisStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.DailyRow;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.GrowthStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LabelCount;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LearningStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.Metric;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RankRow;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentFeedback;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentProblem;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentSolve;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentUser;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RoomStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.UserStats;
import com.aisip.OnO.backend.admin.repository.AdminStatsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 통계 화면에 쓰는 지표를 모은다.
 *
 * <p>증감률은 선택한 기간과 바로 앞의 같은 길이 기간을 비교한다. 7일을 보고 있으면 그 전 7일과 비교한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminStatsQueryRepository statsQueryRepository;

    public Period period(LocalDate start, LocalDate end) {
        return new Period(start, end);
    }

    /**
     * 통계 화면 한 장에 필요한 지표를 읽기 전용 트랜잭션 하나에서 모두 센다.
     *
     * <p>예전에는 컨트롤러가 이 클래스의 메서드를 하나씩(각자 트랜잭션) 부른 뒤, 순위 두 개를 트랜잭션 밖에서
     * 리포지토리로 바로 불렀다. open-in-view 가 켜져 있으면 첫 트랜잭션의 커넥션을 요청 끝까지 쥔 채로,
     * 트랜잭션 밖의 {@code JdbcTemplate} 호출이 풀에서 커넥션을 하나 더 받는다. 요청 하나가 커넥션 두 개를 쓰니
     * 풀 크기만큼 통계 요청이 겹치면 모두 두 번째 커넥션을 기다리며 30초 뒤에 함께 실패했다. (#405)
     * 한 트랜잭션 안에서는 {@code JdbcTemplate} 도 트랜잭션에 묶인 커넥션을 그대로 쓰므로 요청 하나가 하나만 쓴다.
     */
    public AnalysisPage analysisPage(LocalDate start, LocalDate end, LocalDate today) {
        Period period = period(start, end);
        Daily daily = daily(start, end);
        return new AnalysisPage(
                period,
                daily,
                userStats(period, daily, today),
                learningStats(period, daily),
                analysisStats(period),
                growthStats(period, daily),
                roomStats(period),
                statsQueryRepository.topProblemWriters(start, end, 10),
                statsQueryRepository.topSolvers(start, end, 10)
        );
    }

    public Daily daily(LocalDate start, LocalDate end) {
        return new Daily(
                statsQueryRepository.dailyActiveUsers(start, end),
                statsQueryRepository.dailyNewUsers(start, end),
                statsQueryRepository.dailyProblems(start, end),
                statsQueryRepository.dailySolves(start, end),
                statsQueryRepository.dailyPracticeNotes(start, end),
                statsQueryRepository.dailyMissionsCompleted(start, end),
                statsQueryRepository.dailyRoomActivity(start, end)
        );
    }

    public UserStats userStats(Period period, Daily daily, LocalDate today) {
        Period previous = period.previous();
        long days = period.days();
        double averageDau = (double) sum(daily.activeUsers()) / days;
        double previousAverageDau = (double) sum(statsQueryRepository.dailyActiveUsers(previous.start(), previous.end())) / days;

        long wau = statsQueryRepository.countActiveUsers(period.end().minusDays(6), period.end());
        long mau = statsQueryRepository.countActiveUsers(period.end().minusDays(29), period.end());

        return new UserStats(
                statsQueryRepository.countUsers(),
                statsQueryRepository.countNotificationEnabledUsers(),
                statsQueryRepository.countUsersWithFcmToken(),
                new Metric(sum(daily.newUsers()), statsQueryRepository.countSignups(previous.start(), previous.end())),
                new Metric(statsQueryRepository.countActiveUsers(period.start(), period.end()),
                        statsQueryRepository.countActiveUsers(previous.start(), previous.end())),
                new Metric(averageDau, previousAverageDau),
                wau,
                mau,
                mau == 0 ? 0.0 : averageDau * 100.0 / mau,
                statsQueryRepository.retention(period.start(), period.end(), 1, today),
                statsQueryRepository.retention(period.start(), period.end(), 7, today),
                statsQueryRepository.signupsByPlatform(period.start(), period.end())
        );
    }

    public LearningStats learningStats(Period period, Daily daily) {
        Period previous = period.previous();
        Map<String, Long> answers = statsQueryRepository.solvesByAnswerStatus(period.start(), period.end());
        return new LearningStats(
                statsQueryRepository.countAll("problem"),
                statsQueryRepository.countAll("problem_solve"),
                statsQueryRepository.countAll("practice_note"),
                new Metric(sum(daily.problems()), statsQueryRepository.countProblems(previous.start(), previous.end())),
                statsQueryRepository.countProblemWriters(period.start(), period.end()),
                new Metric(sum(daily.solves()), statsQueryRepository.countSolves(previous.start(), previous.end())),
                statsQueryRepository.countSolvers(period.start(), period.end()),
                answers.getOrDefault("CORRECT", 0L),
                answers.getOrDefault("WRONG", 0L),
                answers.getOrDefault("PARTIAL", 0L),
                statsQueryRepository.averageSolveSeconds(period.start(), period.end()),
                statsQueryRepository.countSolvesWithReflection(period.start(), period.end()),
                new Metric(sum(daily.practiceNotes()), statsQueryRepository.countPracticeNotes(previous.start(), previous.end())),
                statsQueryRepository.countPracticeNoteFirstCompletions(period.start(), period.end()),
                statsQueryRepository.countInRange("tag", "created_at", period.start(), period.end()),
                statsQueryRepository.countInRange("folder", "created_at", period.start(), period.end()),
                statsQueryRepository.countInRange("learning_calendar_mood", "created_at", period.start(), period.end()),
                statsQueryRepository.countSolveMoods(period.start(), period.end())
        );
    }

    public AnalysisStats analysisStats(Period period) {
        List<LabelCount> all = statsQueryRepository.analysisStatuses();
        List<LabelCount> inPeriod = statsQueryRepository.analysisStatuses(period.start(), period.end());
        return new AnalysisStats(
                all,
                inPeriod,
                all.stream().mapToLong(LabelCount::count).sum(),
                inPeriod.stream().mapToLong(LabelCount::count).sum(),
                statsQueryRepository.analysisSubjects(period.start(), period.end(), 8)
        );
    }

    public GrowthStats growthStats(Period period, Daily daily) {
        List<LabelCount> achievements = statsQueryRepository.achievementsEarned(period.start(), period.end()).stream()
                .map(row -> new LabelCount(
                        Achievement.fromKey(row.label()).map(Achievement::getNameKo).orElse(row.label()),
                        row.count()))
                .toList();
        return new GrowthStats(
                sum(daily.missionsCompleted()),
                statsQueryRepository.countMissionsClaimed(period.start(), period.end()),
                statsQueryRepository.topCompletedMissions(period.start(), period.end(), 6),
                achievements.stream().mapToLong(LabelCount::count).sum(),
                achievements.stream().limit(6).toList(),
                statsQueryRepository.countCosmeticUsers(),
                statsQueryRepository.levelDistribution()
        );
    }

    public RoomStats roomStats(Period period) {
        LocalDate start = period.start();
        LocalDate end = period.end();
        return new RoomStats(
                statsQueryRepository.countAll("study_room"),
                statsQueryRepository.countInRange("study_room", "created_at", start, end),
                statsQueryRepository.countInRange("study_room_member", "created_at", start, end),
                statsQueryRepository.countInRange("study_room_shared_problem", "created_at", start, end),
                statsQueryRepository.countInRange("study_room_shared_problem_comment", "created_at", start, end),
                statsQueryRepository.countRoomReactions(start, end),
                statsQueryRepository.countInRange("study_room_challenge", "created_at", start, end),
                statsQueryRepository.countChallengesCompleted(start, end),
                statsQueryRepository.countChallengesFailed(start, end)
        );
    }

    /** 관리자 홈의 오늘 현황. 어제와 비교한다. */
    public Home home(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        Map<LocalDate, Long> recentDau = statsQueryRepository.dailyActiveUsers(today.minusDays(13), today);
        return new Home(
                new Metric(recentDau.get(today), recentDau.get(yesterday)),
                new Metric(statsQueryRepository.countSignups(today, today), statsQueryRepository.countSignups(yesterday, yesterday)),
                new Metric(statsQueryRepository.countProblems(today, today), statsQueryRepository.countProblems(yesterday, yesterday)),
                new Metric(statsQueryRepository.countSolves(today, today), statsQueryRepository.countSolves(yesterday, yesterday)),
                recentDau,
                recentDau.values().stream().mapToLong(Long::longValue).max().orElse(0L),
                statsQueryRepository.recentUsers(8),
                statsQueryRepository.recentProblems(8),
                statsQueryRepository.recentSolves(8),
                statsQueryRepository.recentFeedbacks(3),
                statsQueryRepository.analysisStatuses()
        );
    }

    public static long sum(Map<LocalDate, Long> daily) {
        return daily.values().stream().mapToLong(Long::longValue).sum();
    }

    public record Home(
            Metric activeUsers,
            Metric signups,
            Metric problems,
            Metric solves,
            Map<LocalDate, Long> recentDau,
            long recentDauMax,
            List<RecentUser> recentUsers,
            List<RecentProblem> recentProblems,
            List<RecentSolve> recentSolves,
            List<RecentFeedback> recentFeedbacks,
            List<LabelCount> analysisStatuses
    ) {

        public long analysisCount(String status) {
            return analysisStatuses.stream()
                    .filter(s -> s.label().equals(status))
                    .mapToLong(LabelCount::count)
                    .sum();
        }

        /** CSS 막대 높이(%). 최댓값이 0 이면 모든 막대를 바닥에 둔다. */
        public double barHeight(long value) {
            return recentDauMax == 0 ? 0.0 : value * 100.0 / recentDauMax;
        }
    }

    public record AnalysisPage(
            Period period,
            Daily daily,
            UserStats userStats,
            LearningStats learningStats,
            AnalysisStats analysisStats,
            GrowthStats growthStats,
            RoomStats roomStats,
            List<RankRow> topProblemWriters,
            List<RankRow> topSolvers
    ) {
    }

    public record Period(LocalDate start, LocalDate end) {

        public long days() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }

        public Period previous() {
            return new Period(start.minusDays(days()), start.minusDays(1));
        }
    }

    public record Daily(
            Map<LocalDate, Long> activeUsers,
            Map<LocalDate, Long> newUsers,
            Map<LocalDate, Long> problems,
            Map<LocalDate, Long> solves,
            Map<LocalDate, Long> practiceNotes,
            Map<LocalDate, Long> missionsCompleted,
            Map<LocalDate, Long> roomActivity
    ) {

        /** 표는 최근 날짜가 위로 오게 뒤집어서 준다. */
        public List<DailyRow> rowsNewestFirst() {
            List<DailyRow> rows = new ArrayList<>();
            for (LocalDate date : activeUsers.keySet()) {
                rows.add(new DailyRow(date,
                        activeUsers.get(date), newUsers.get(date), problems.get(date), solves.get(date),
                        practiceNotes.get(date), missionsCompleted.get(date), roomActivity.get(date)));
            }
            Collections.reverse(rows);
            return rows;
        }

        public DailyRow totals() {
            return new DailyRow(null, sum(activeUsers), sum(newUsers), sum(problems), sum(solves),
                    sum(practiceNotes), sum(missionsCompleted), sum(roomActivity));
        }
    }
}
