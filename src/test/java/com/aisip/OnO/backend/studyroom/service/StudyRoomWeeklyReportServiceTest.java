package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주간 리포트 배치 생성 검증.
 *
 * <p>배치는 지난주(월~일) 활동을 집계해 방마다 리포트 한 건을 만든다. 활동이 없는 방도
 * 리포트가 나와야 하고, 그때 값이 null 이 아니라 0 이어야 한다 — 응답 DTO 의 숫자 필드가
 * null 이면 앱에서 그대로 터진다.
 */
@DisplayName("StudyRoomWeeklyReportService — 주간 리포트 배치")
class StudyRoomWeeklyReportServiceTest extends StudyRoomTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private StudyRoomWeeklyReportService reportService;

    private LocalDate lastWeekStart;

    @BeforeEach
    void resolveLastWeek() {
        lastWeekStart = LocalDate.now(KST).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
    }

    @Nested
    @DisplayName("생성")
    class CreateReports {

        @Test
        @DisplayName("활동이 없는 방도 0 기반 리포트를 받는다")
        void emptyRoomGetsZeroBasedReport() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "조용한 방");

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getRoom().getId()).as("대상 방").isEqualTo(room.getId());
                        assertThat(report.getWeekStart()).as("주 시작일").isEqualTo(lastWeekStart);
                        assertThat(report.getWeekEnd()).as("주 종료일").isEqualTo(lastWeekStart.plusDays(6));
                        assertThat(report.getTopMemberProblemCount()).as("탑 멤버 문제 수").isZero();
                        assertThat(report.getLongestStreakDays()).as("최장 스트릭").isZero();
                        assertThat(report.getTotalProblems()).as("총 문제 수").isZero();
                        assertThat(report.getChallengesCompleted()).as("완료 챌린지 수").isZero();
                        assertThat(report.getCheerMessage()).as("응원 메시지").isNotBlank();
                    });
        }

        @Test
        @DisplayName("멤버가 한 명도 없는 방은 이름 필드가 비어 있고 숫자는 0 이다")
        void roomWithoutMembersHasNullNamesAndZeroCounts() {
            StudyRoom room = roomRepository.saveAndFlush(StudyRoom.create("멤버 없는 방", 1L));

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getRoom().getId()).isEqualTo(room.getId());
                        assertThat(report.getTopMemberName()).as("탑 멤버 이름").isNull();
                        assertThat(report.getTopMemberProfileImageUrl()).as("탑 멤버 프로필").isNull();
                        assertThat(report.getLongestStreakName()).as("최장 스트릭 이름").isNull();
                        assertThat(report.getTotalProblems()).as("총 문제 수").isZero();
                    });
        }

        @Test
        @DisplayName("리포트 주간 밖의 활동은 집계되지 않는다")
        void activityOutsideTheReportWeekIsExcluded() {
            User host = fixtures.createUser("host");
            createRoom(host, "활동 방");
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));
            saveProblemCreatedAt(host.getId(), lastWeekStart.plusDays(2).atTime(10, 0));
            saveProblemCreatedAt(host.getId(), lastWeekStart.minusDays(1).atTime(10, 0));
            saveProblemCreatedAt(host.getId(), lastWeekStart.plusDays(7).atTime(10, 0));

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getTotalProblems()).as("리포트 주간의 총 문제 수").isEqualTo(2);
                        assertThat(report.getTopMemberProblemCount()).as("탑 멤버 문제 수").isEqualTo(2);
                        assertThat(report.getTopMemberName()).as("탑 멤버 이름").isEqualTo(host.getName());
                    });
        }

        /**
         * 배치가 대상으로 삼는 주는 {@code TemporalAdjusters.previous(MONDAY)} 로 정해진다.
         * 월요일에 돌면 직전 완료 주(월~일)가 되지만, 다른 요일에 돌면 "지금 진행 중인 주"가 잡힌다.
         * Quartz 트리거가 {@code 0 0 8 ? * MON} 이라 운영에서는 월요일에만 돌지만,
         * 수동 실행 시에는 의도와 다른 주가 나올 수 있다는 점을 여기 고정해 둔다.
         */
        @Test
        @DisplayName("대상 주는 previous(MONDAY) 기준으로 정해진다")
        void reportWeekIsPreviousMondayBased() {
            User host = fixtures.createUser("host");
            createRoom(host, "기준 주 방");

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getWeekStart().getDayOfWeek())
                                .as("주 시작 요일").isEqualTo(DayOfWeek.MONDAY);
                        assertThat(report.getWeekStart())
                                .as("주 시작일").isEqualTo(lastWeekStart);
                        assertThat(report.getWeekEnd())
                                .as("주 종료일").isEqualTo(lastWeekStart.plusDays(6));
                    });
        }

        @Test
        @DisplayName("문제를 가장 많이 등록한 멤버가 탑 멤버로 뽑히고 총합은 전원 합계다")
        void topMemberIsTheOneWithMostProblems() {
            User host = fixtures.createUser("host");
            User member = fixtures.createUser("member");
            StudyRoom room = createRoom(host, "경쟁 방");
            addMember(room, member);
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.atTime(11, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.plusDays(1).atTime(11, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.plusDays(2).atTime(11, 0));

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getTopMemberName()).as("탑 멤버").isEqualTo(member.getName());
                        assertThat(report.getTopMemberProblemCount()).as("탑 멤버 문제 수").isEqualTo(3);
                        assertThat(report.getTotalProblems()).as("방 전체 문제 수").isEqualTo(4);
                    });
        }

        @Test
        @DisplayName("탑 멤버의 프로필 이미지 URL 이 함께 저장된다")
        void topMemberProfileImageIsCopied() {
            User host = fixtures.createUser("host");
            host.updateProfileImageUrl("https://cdn.example.com/host.png");
            userRepository.saveAndFlush(host);
            createRoom(host, "프로필 방");
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> assertThat(report.getTopMemberProfileImageUrl())
                            .as("탑 멤버 프로필 이미지")
                            .isEqualTo("https://cdn.example.com/host.png"));
        }

        @Test
        @DisplayName("지난주에 끝난 완료 챌린지 수가 집계된다")
        void completedChallengesInLastWeekAreCounted() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "챌린지 방");
            StudyRoomChallenge completed = saveChallenge(room, "완료", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    lastWeekStart.minusDays(3).atStartOfDay(), lastWeekStart.plusDays(2).atTime(12, 0));
            completed.updateStatus(StudyRoomChallengeStatus.COMPLETED);
            challengeRepository.saveAndFlush(completed);
            StudyRoomChallenge outsideWeek = saveChallenge(room, "지지난주 완료", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    lastWeekStart.minusDays(20).atStartOfDay(), lastWeekStart.minusDays(10).atTime(12, 0));
            outsideWeek.updateStatus(StudyRoomChallengeStatus.COMPLETED);
            challengeRepository.saveAndFlush(outsideWeek);

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("생성된 리포트")
                    .singleElement()
                    .satisfies(report -> assertThat(report.getChallengesCompleted())
                            .as("지난주에 끝난 완료 챌린지 수").isEqualTo(1));
        }

        @Test
        @DisplayName("여러 방이 있으면 방마다 리포트가 하나씩 만들어진다")
        void oneReportPerRoom() {
            User host = fixtures.createUser("host");
            StudyRoom first = createRoom(host, "첫방");
            StudyRoom second = createRoom(host, "둘째방");

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("방마다 하나씩 생성된 리포트")
                    .hasSize(2)
                    .extracting(report -> report.getRoom().getId())
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }

        @Test
        @DisplayName("같은 주에 두 번 실행해도 리포트는 하나만 남는다")
        void runningTwiceIsIdempotent() {
            User host = fixtures.createUser("host");
            createRoom(host, "멱등 방");

            reportService.createPreviousWeekReports();
            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll()).as("생성된 리포트").hasSize(1);
        }

        @Test
        @DisplayName("이미 같은 주 리포트가 있으면 덮어쓰지 않는다")
        void existingReportIsNotOverwritten() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "기존 리포트 방");
            StudyRoomWeeklyReport existing = saveWeeklyReport(room, lastWeekStart);

            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll())
                    .as("리포트")
                    .singleElement()
                    .satisfies(report -> {
                        assertThat(report.getId()).as("기존 리포트 ID").isEqualTo(existing.getId());
                        assertThat(report.getTotalProblems()).as("기존 값 유지").isEqualTo(20);
                    });
        }

        @Test
        @DisplayName("방이 하나도 없으면 아무 리포트도 만들지 않는다")
        void noRoomsMeansNoReports() {
            reportService.createPreviousWeekReports();

            assertThat(weeklyReportRepository.findAll()).as("생성된 리포트").isEmpty();
        }

        @Test
        @DisplayName("배치 실행 중 모든 멤버가 채운 개인 챌린지는 completed 로 정리되고 완료 시각이 남는다")
        void completedIndividualChallengeIsRefreshed() {
            User host = fixtures.createUser("host");
            User member = fixtures.createUser("member");
            StudyRoom room = createRoom(host, "개인 챌린지 방");
            addMember(room, member);
            StudyRoomChallenge challenge = saveChallenge(room, "각자 1문제",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1,
                    lastWeekStart.minusDays(1).atStartOfDay(), lastWeekStart.plusDays(20).atStartOfDay());
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));
            saveProblemCreatedAt(member.getId(), lastWeekStart.atTime(11, 0));

            reportService.createPreviousWeekReports();

            StudyRoomChallenge reloaded = challengeRepository.findById(challenge.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).as("배치 후 챌린지 상태")
                    .isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reloaded.getCompletedAt()).as("완료 시각").isNotNull();
        }

        @Test
        @DisplayName("한 명이라도 못 채운 개인 챌린지는 진행 중으로 남는다")
        void unfinishedIndividualChallengeStaysInProgress() {
            User host = fixtures.createUser("host");
            User member = fixtures.createUser("member");
            StudyRoom room = createRoom(host, "미완 챌린지 방");
            addMember(room, member);
            StudyRoomChallenge challenge = saveChallenge(room, "각자 1문제",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1,
                    lastWeekStart.minusDays(1).atStartOfDay(), lastWeekStart.plusDays(20).atStartOfDay());
            saveProblemCreatedAt(host.getId(), lastWeekStart.atTime(10, 0));

            reportService.createPreviousWeekReports();

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("배치 후 챌린지 상태")
                    .isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("배치 실행 중 진행 중이던 만료 챌린지는 expired 로 정리된다")
        void expiredChallengesAreRefreshed() {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "정리 방");
            StudyRoomChallenge expired = saveChallenge(room, "만료됨", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1_000,
                    lastWeekStart.minusDays(3).atStartOfDay(), lastWeekStart.plusDays(1).atTime(12, 0));

            reportService.createPreviousWeekReports();

            assertThat(challengeRepository.findById(expired.getId()).orElseThrow().getStatus())
                    .as("배치 후 챌린지 상태")
                    .isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }
    }

}
