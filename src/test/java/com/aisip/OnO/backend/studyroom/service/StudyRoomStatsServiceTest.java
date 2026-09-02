package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스터디룸 통계 집계 검증.
 *
 * <p>여기 값들은 방 상세·챌린지 진행도·주간 리포트에 그대로 흘러간다. 사용자별로 분리되지
 * 않으면 남의 학습량이 내 통계에 섞이므로, 여러 사용자를 섞은 상태에서 확인한다.
 */
@DisplayName("StudyRoomStatsService — 학습 통계 집계")
class StudyRoomStatsServiceTest extends StudyRoomTestSupport {

    @Autowired
    private StudyRoomStatsService statsService;

    @Nested
    @DisplayName("복습 수")
    class PracticeCounts {

        @Test
        @DisplayName("오늘 복습 수는 사용자별로 분리된다")
        void todayPracticeCountsArePerUser() {
            User first = fixtures.createUser("first");
            User second = fixtures.createUser("second");
            savePractices(first.getId(), LocalDateTime.now(), 3);
            savePractices(second.getId(), LocalDateTime.now(), 1);

            Map<Long, Integer> counts = statsService.getTodayPracticeCounts(List.of(first.getId(), second.getId()));

            assertThat(counts.get(first.getId())).as("첫 사용자의 오늘 복습 수").isEqualTo(3);
            assertThat(counts.get(second.getId())).as("둘째 사용자의 오늘 복습 수").isEqualTo(1);
        }

        @Test
        @DisplayName("어제 복습은 오늘 복습 수에 들어가지 않는다")
        void yesterdayPracticeIsExcludedFromToday() {
            User user = fixtures.createUser("user");
            savePractices(user.getId(), LocalDateTime.now().minusDays(1), 5);

            Map<Long, Integer> counts = statsService.getTodayPracticeCounts(List.of(user.getId()));

            assertThat(counts.getOrDefault(user.getId(), 0)).as("오늘 복습 수").isZero();
        }

        @Test
        @DisplayName("복습이 없는 사용자는 집계에 등장하지 않는다")
        void userWithoutPracticeIsAbsent() {
            User user = fixtures.createUser("user");

            Map<Long, Integer> counts = statsService.getTodayPracticeCounts(List.of(user.getId()));

            assertThat(counts.getOrDefault(user.getId(), 0)).as("복습 없는 사용자").isZero();
        }

        @Test
        @DisplayName("사용자 목록이 비면 빈 결과를 준다")
        void emptyUserListYieldsEmptyResult() {
            assertThat(statsService.getTodayPracticeCounts(List.of())).as("빈 입력의 결과").isEmpty();
        }
    }

    @Nested
    @DisplayName("주간 통계")
    class WeeklyStats {

        @Test
        @DisplayName("이번 주 문제 등록 수와 복습 수가 함께 나온다")
        void weeklyStatsIncludeProblemsAndPractices() {
            User user = fixtures.createUser("user");
            saveProblem(user.getId());
            saveProblem(user.getId());
            savePracticesOnly(user.getId(), oldProblem(user.getId()), LocalDateTime.now(), 4);

            Map<Long, StudyRoomStats> stats = statsService.getWeeklyStats(List.of(user.getId()));

            assertThat(stats.get(user.getId())).as("주간 통계").satisfies(value -> {
                assertThat(value.weeklyProblemCount()).as("문제 등록 수").isEqualTo(2);
                assertThat(value.weeklyPracticeCount()).as("복습 수").isEqualTo(4);
            });
        }

        @Test
        @DisplayName("활동이 없어도 0 으로 채운 항목이 나온다")
        void inactiveUserGetsZeroStats() {
            User user = fixtures.createUser("user");

            Map<Long, StudyRoomStats> stats = statsService.getWeeklyStats(List.of(user.getId()));

            assertThat(stats.get(user.getId())).as("활동 없는 사용자의 통계")
                    .isEqualTo(new StudyRoomStats(0, 0, 0));
        }

        @Test
        @DisplayName("남의 활동은 내 통계에 섞이지 않는다")
        void otherUsersActivityIsNotMixedIn() {
            User me = fixtures.createUser("me");
            User other = fixtures.createUser("other");
            saveProblem(other.getId());
            savePracticesOnly(other.getId(), oldProblem(other.getId()), LocalDateTime.now(), 3);

            Map<Long, StudyRoomStats> stats = statsService.getWeeklyStats(List.of(me.getId(), other.getId()));

            assertThat(stats.get(me.getId())).as("내 통계").isEqualTo(new StudyRoomStats(0, 0, 0));
            assertThat(stats.get(other.getId()).weeklyPracticeCount()).as("상대 복습 수").isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("연속 학습일")
    class Streaks {

        @Test
        @DisplayName("오늘부터 이어진 날만큼 스트릭이 쌓인다")
        void consecutiveDaysAreCounted() {
            User user = fixtures.createUser("user");
            LocalDate today = LocalDate.now();
            var problem = oldProblem(user.getId());
            savePracticesOnly(user.getId(), problem, today.atTime(LocalTime.NOON), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(1).atTime(LocalTime.NOON), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(2).atTime(LocalTime.NOON), 1);

            Map<Long, Integer> streaks = statsService.currentStreaks(List.of(user.getId()), today);

            assertThat(streaks.get(user.getId())).as("연속 학습일").isEqualTo(3);
        }

        @Test
        @DisplayName("중간에 하루 비면 거기서 끊긴다")
        void gapBreaksStreak() {
            User user = fixtures.createUser("user");
            LocalDate today = LocalDate.now();
            var problem = oldProblem(user.getId());
            savePracticesOnly(user.getId(), problem, today.atTime(LocalTime.NOON), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(2).atTime(LocalTime.NOON), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(3).atTime(LocalTime.NOON), 1);

            Map<Long, Integer> streaks = statsService.currentStreaks(List.of(user.getId()), today);

            assertThat(streaks.get(user.getId())).as("끊긴 뒤 연속 학습일").isEqualTo(1);
        }

        @Test
        @DisplayName("오늘 학습하지 않았어도 어제까지 이어졌으면 스트릭은 유지된다")
        void streakSurvivesWhenTodayIsStillOpen() {
            User user = fixtures.createUser("user");
            LocalDate today = LocalDate.now();
            var problem = oldProblem(user.getId());
            savePracticesOnly(user.getId(), problem, today.minusDays(1).atTime(LocalTime.NOON), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(2).atTime(LocalTime.NOON), 1);

            Map<Long, Integer> streaks = statsService.currentStreaks(List.of(user.getId()), today);

            assertThat(streaks.get(user.getId())).as("오늘 미학습 시 연속 학습일").isEqualTo(2);
        }

        @Test
        @DisplayName("활동이 전혀 없으면 0 이다")
        void noActivityMeansZeroStreak() {
            User user = fixtures.createUser("user");

            Map<Long, Integer> streaks = statsService.currentStreaks(List.of(user.getId()), LocalDate.now());

            assertThat(streaks.get(user.getId())).as("활동 없는 사용자의 연속 학습일").isZero();
        }

        @Test
        @DisplayName("문제 등록만 해도 학습일로 인정된다")
        void problemRegistrationCountsAsStudyDay() {
            User user = fixtures.createUser("user");
            saveProblem(user.getId());

            Map<Long, Integer> streaks = statsService.currentStreaks(List.of(user.getId()), LocalDate.now());

            assertThat(streaks.get(user.getId())).as("문제 등록만 한 날의 연속 학습일").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("출석일 수")
    class AttendanceDays {

        @Test
        @DisplayName("같은 날 여러 번 활동해도 하루로 센다")
        void multipleActivitiesOnSameDayCountOnce() {
            User user = fixtures.createUser("user");
            LocalDate today = LocalDate.now();
            var problem = oldProblem(user.getId());
            savePracticesOnly(user.getId(), problem, today.atTime(9, 0), 3);
            savePracticesOnly(user.getId(), problem, today.atTime(20, 0), 2);

            Map<Long, Integer> attendance = statsService.attendanceDayCounts(
                    List.of(user.getId()), today.minusDays(7).atStartOfDay(), today.atTime(LocalTime.MAX));

            assertThat(attendance.get(user.getId())).as("출석일 수").isEqualTo(1);
        }

        @Test
        @DisplayName("서로 다른 날의 활동은 각각 하루로 센다")
        void distinctDaysAreCountedSeparately() {
            User user = fixtures.createUser("user");
            LocalDate today = LocalDate.now();
            var problem = oldProblem(user.getId());
            savePracticesOnly(user.getId(), problem, today.atTime(9, 0), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(2).atTime(9, 0), 1);
            savePracticesOnly(user.getId(), problem, today.minusDays(4).atTime(9, 0), 1);

            Map<Long, Integer> attendance = statsService.attendanceDayCounts(
                    List.of(user.getId()), today.minusDays(7).atStartOfDay(), today.atTime(LocalTime.MAX));

            assertThat(attendance.get(user.getId())).as("출석일 수").isEqualTo(3);
        }

        @Test
        @DisplayName("사용자 목록이 비면 빈 결과를 준다")
        void emptyUserListYieldsEmptyResult() {
            assertThat(statsService.attendanceDayCounts(
                    List.of(), LocalDateTime.now().minusDays(7), LocalDateTime.now()))
                    .as("빈 입력의 결과").isEmpty();
        }
    }

    /**
     * 아주 오래전에 등록된 문제.
     *
     * <p>문제 등록도 학습 활동으로 집계된다. 복습만 있는 상황을 만들려면 복습을 붙일 문제의
     * 등록일이 관심 구간 밖에 있어야 한다.
     */
    private com.aisip.OnO.backend.problem.entity.Problem oldProblem(Long userId) {
        return saveProblemCreatedAt(userId, LocalDate.now().minusYears(1).atStartOfDay());
    }

    @Nested
    @DisplayName("방별 오늘 복습 요약")
    class TodayPracticeSummaryByRoom {

        @Test
        @DisplayName("방의 멤버 전원을 합산해 복습한 인원 수와 총 복습 수를 준다")
        void summaryAggregatesRoomMembers() {
            User host = fixtures.createUser("host");
            User member = fixtures.createUser("member");
            StudyRoom room = createRoom(host, "요약 방");
            addMember(room, member);
            savePractices(host.getId(), LocalDateTime.now(), 2);
            savePractices(member.getId(), LocalDateTime.now(), 3);

            var summaries = statsService.getTodayPracticeSummariesByRoomIds(List.of(room.getId()));

            assertThat(summaries.get(room.getId())).as("방 요약").satisfies(summary -> {
                assertThat(summary.todayPracticeMemberCount()).as("복습한 인원 수").isEqualTo(2);
                assertThat(summary.todayPracticeCount()).as("총 복습 수").isEqualTo(5);
            });
        }

        @Test
        @DisplayName("아무도 복습하지 않은 방은 요약에 등장하지 않는다")
        void roomWithoutPracticeIsAbsent() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "조용한 방");

            var summaries = statsService.getTodayPracticeSummariesByRoomIds(List.of(room.getId()));

            assertThat(summaries).as("복습 없는 방의 요약").doesNotContainKey(room.getId());
        }

        @Test
        @DisplayName("방 목록이 비면 빈 결과를 준다")
        void emptyRoomListYieldsEmptyResult() {
            assertThat(statsService.getTodayPracticeSummariesByRoomIds(List.of()))
                    .as("빈 입력의 결과").isEmpty();
        }
    }
}
