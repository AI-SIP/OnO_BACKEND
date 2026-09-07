package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeMetric;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeStatus;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeType;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReport;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주간 리포트를 만들면서 함께 도는 챌린지 상태 정리 검증.
 *
 * <p>리포트 배치는 지난주를 마감하는 자리라, 그 주에 목표를 채운 챌린지를 여기서 완료로 확정하고
 * 리포트의 "달성한 챌린지 수"에 반영한다. 단체 챌린지의 합산, 출석 지표, 멤버가 없는 방처럼
 * 평상시 조회 경로에서는 잘 밟히지 않는 갈래가 여기 모여 있다.
 */
@DisplayName("주간 리포트 — 챌린지 정리")
class StudyRoomWeeklyReportChallengeTest extends StudyRoomTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private StudyRoomWeeklyReportService reportService;

    private User host;
    private User member;
    private StudyRoom room;
    private LocalDate lastWeekStart;
    private LocalDate lastWeekEnd;
    /** 요일과 무관하게 이미 끝난 기간. 만료 검증에 쓴다. */
    private LocalDate pastStart;
    private LocalDate pastEnd;

    @BeforeEach
    void setUpRoom() {
        host = fixtures.createUser("host");
        member = fixtures.createUser("member");
        room = createRoom(host, "리포트방");
        addMember(room, member);
        lastWeekStart = LocalDate.now(KST).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        lastWeekEnd = lastWeekStart.plusDays(6);
        pastEnd = LocalDate.now(KST).minusDays(2);
        pastStart = pastEnd.minusDays(6);
    }

    private StudyRoomChallenge saveLastWeekChallenge(StudyRoom targetRoom, StudyRoomChallengeType type,
                                                     StudyRoomChallengeMetric metric, int targetValue) {
        return saveChallenge(targetRoom, "지난주 챌린지", type, metric, null, null, targetValue,
                lastWeekStart.atStartOfDay(), lastWeekEnd.atTime(23, 59, 59));
    }

    /**
     * 기간이 확실히 지난 챌린지를 만든다.
     *
     * <p>{@code saveLastWeekChallenge} 가 쓰는 {@code TemporalAdjusters.previous(MONDAY)} 는
     * "가장 최근 월요일"이라, 월요일이 아닌 날 실행하면 그 주의 일요일이 미래가 된다.
     * 만료 판정은 {@code endAt.isBefore(now)} 이므로 그 경우 만료되지 않아, 월요일에만 통과하는
     * 테스트가 된다. 만료를 검증할 때는 요일과 무관하게 과거인 기간을 쓴다.
     */
    private StudyRoomChallenge savePastChallenge(StudyRoom targetRoom, StudyRoomChallengeType type,
                                                 StudyRoomChallengeMetric metric, int targetValue) {
        return saveChallenge(targetRoom, "지난 기간 챌린지", type, metric, null, null, targetValue,
                pastStart.atStartOfDay(), pastEnd.atTime(23, 59, 59));
    }

    private StudyRoomChallengeStatus statusOf(StudyRoomChallenge challenge) {
        return challengeRepository.findById(challenge.getId()).orElseThrow().getStatus();
    }

    private StudyRoomWeeklyReport reportOf(StudyRoom targetRoom) {
        return weeklyReportRepository.findAll().stream()
                .filter(report -> report.getRoom().getId().equals(targetRoom.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("단체 챌린지")
    class GroupChallenge {

        @Test
        @DisplayName("멤버들의 합계가 목표에 닿으면 완료로 확정되고 리포트에 집계된다")
        void completesWhenSumReachesTarget() {
            StudyRoomChallenge challenge = saveLastWeekChallenge(
                    room, StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT, 3);
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.plusDays(1).atTime(10, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.plusDays(2).atTime(10, 0));

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge))
                    .as("혼자서는 못 채워도 방 전체 합계가 목표에 닿으면 달성이다")
                    .isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reportOf(room).getChallengesCompleted()).isEqualTo(1);
        }

        @Test
        @DisplayName("합계가 목표에 못 미치면 기간이 지난 것으로 보고 만료된다")
        void expiresWhenSumFallsShort() {
            StudyRoomChallenge challenge = savePastChallenge(
                    room, StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT, 10);
            saveProblemCreatedAt(host.getId(), pastStart.atTime(10, 0));

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge)).isEqualTo(StudyRoomChallengeStatus.EXPIRED);
            assertThat(reportOf(room).getChallengesCompleted()).isZero();
        }
    }

    @Nested
    @DisplayName("개인 챌린지")
    class IndividualChallenge {

        @Test
        @DisplayName("출석 지표는 학습한 날 수로 판정하고 모두 채워야 완료된다")
        void completesAttendanceWhenEveryMemberAttends() {
            StudyRoomChallenge challenge = saveLastWeekChallenge(
                    room, StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.ATTENDANCE, 2);
            for (User user : List.of(host, member)) {
                saveProblemCreatedAt(user.getId(), lastWeekStart.atTime(9, 0));
                saveProblemCreatedAt(user.getId(), lastWeekStart.plusDays(1).atTime(9, 0));
            }

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge)).isEqualTo(StudyRoomChallengeStatus.COMPLETED);
        }

        @Test
        @DisplayName("하루에 여러 번 학습해도 출석은 하루로 센다")
        void countsAttendanceByDistinctDay() {
            StudyRoomChallenge challenge = savePastChallenge(
                    room, StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.ATTENDANCE, 2);
            for (User user : List.of(host, member)) {
                saveProblemCreatedAt(user.getId(), pastStart.atTime(9, 0));
                saveProblemCreatedAt(user.getId(), pastStart.atTime(20, 0));
            }

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge))
                    .as("같은 날 두 번 공부한 것을 이틀로 세면 안 된다")
                    .isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }

        @Test
        @DisplayName("복습 지표는 복습 횟수로 판정하며 한 명이라도 못 채우면 완료되지 않는다")
        void practiceMetricNeedsEveryMember() {
            StudyRoomChallenge challenge = savePastChallenge(
                    room, StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PRACTICE_COUNT, 2);
            savePracticesOnly(host.getId(), saveProblem(host.getId()), pastStart.atTime(10, 0), 2);
            savePracticesOnly(member.getId(), saveProblem(member.getId()), pastStart.atTime(10, 0), 1);

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge)).isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }

        @Test
        @DisplayName("모두가 복습 목표를 채우면 완료된다")
        void completesWhenEveryMemberPractices() {
            StudyRoomChallenge challenge = saveLastWeekChallenge(
                    room, StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PRACTICE_COUNT, 2);
            savePracticesOnly(host.getId(), saveProblem(host.getId()), lastWeekStart.atTime(10, 0), 2);
            savePracticesOnly(member.getId(), saveProblem(member.getId()), lastWeekStart.atTime(10, 0), 3);

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge)).isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reportOf(room).getChallengesCompleted()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("멤버가 없는 방")
    class EmptyRoom {

        @Test
        @DisplayName("아무도 없는 방의 챌린지는 달성 처리되지 않는다")
        void doesNotCompleteChallengeWithoutMembers() {
            StudyRoom emptyRoom = roomRepository.saveAndFlush(StudyRoom.create("빈방", host.getId()));
            StudyRoomChallenge challenge = saveChallenge(emptyRoom, "미래 챌린지",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    lastWeekStart.atStartOfDay(), LocalDateTime.now().plusDays(7));

            reportService.createPreviousWeekReports();

            assertThat(statusOf(challenge))
                    .as("판정할 멤버가 없는데 '전원 달성' 으로 보면 안 된다")
                    .isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("멤버가 없어도 리포트는 만들어지고 최고 기록은 비어 있다")
        void createsEmptyReport() {
            StudyRoom emptyRoom = roomRepository.saveAndFlush(StudyRoom.create("빈방", host.getId()));

            reportService.createPreviousWeekReports();

            StudyRoomWeeklyReport report = reportOf(emptyRoom);
            assertThat(report.getTopMemberName()).isNull();
            assertThat(report.getLongestStreakName()).isNull();
            assertThat(report.getTotalProblems()).isZero();
            assertThat(report.getWeekStart()).isEqualTo(lastWeekStart);
            assertThat(report.getWeekEnd()).isEqualTo(lastWeekEnd);
        }
    }
}
