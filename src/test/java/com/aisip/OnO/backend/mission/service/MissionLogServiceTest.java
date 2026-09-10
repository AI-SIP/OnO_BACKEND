package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.support.MissionTestSupport;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자동 적립이 <b>켜져 있을 때</b>의 동작. 설정을 건드리지 않은 기본 상태이고, 지금 운영이 이 상태다.
 *
 * <p>꺼졌을 때의 동작은 {@code MissionLegacyAccrualTest} 와 {@code MissionLogRetentionTest} 가 본다.
 * 기본값에 기대지 않고 플래그를 명시적으로 세워, 기본값이 바뀌어도 이 클래스가 무엇을 검증하는지 흔들리지 않게 한다.
 */
@DisplayName("MissionLogService - 자동 적립 켜짐")
class MissionLogServiceTest extends MissionTestSupport {

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
        setLegacyAccrual(true);
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    private void assertMissionError(MissionErrorCase expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting(thrown -> ((ApplicationException) thrown).getErrorCase())
                .isEqualTo(expected);
    }

    private UserMissionStatus statusOf(User target) {
        return reload(target).getUserMissionStatus();
    }

    private PracticeNote savePracticeNote(Long userId, String title) {
        return practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, title, List.of(), null), userId));
    }

    @Nested
    @DisplayName("출석 미션")
    class LoginMission {

        @Test
        @DisplayName("첫 로그인이면 기록을 남기고 출석 경험치를 준다")
        void grantsAttendancePointOnFirstLogin() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.getMissionType()).isEqualTo(MissionType.USER_LOGIN);
                        assertThat(log.getPoint()).isEqualTo(15L);
                    });
            assertThat(statusOf(user)).satisfies(status -> {
                assertThat(accumulatedPoints(status.getAttendanceLevel(), status.getAttendancePoint()))
                        .isEqualTo(15L);
                assertThat(status.getTotalStudyPoint())
                        .as("출석 경험치는 총 학습 포인트에도 합산된다")
                        .isEqualTo(15L);
            });
        }

        @Test
        @DisplayName("같은 날 다시 로그인해도 기록도 경험치도 늘지 않는다")
        void ignoresSecondLoginOfSameDay() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("하루에 여러 번 로그인해도 출석은 한 번이다")
                    .hasSize(1);
            assertThat(statusOf(user).getTotalStudyPoint()).isEqualTo(15L);
        }

        @Test
        @DisplayName("어제 로그인했더라도 오늘 다시 출석 보상을 받는다")
        void grantsAgainOnNewDay() {
            saveMissionLogAt(user, MissionType.USER_LOGIN, null, today().minusDays(1).atTime(23, 0));

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(2);
        }

        @Test
        @DisplayName("다른 사용자의 출석은 내 출석에 영향을 주지 않는다")
        void isolatesUsers() {
            missionLogService.registerLoginMission(other.getId());

            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
            assertThat(statusOf(user).getTotalStudyPoint()).isEqualTo(15L);
            assertThat(statusOf(other).getTotalStudyPoint()).isEqualTo(15L);
        }

        @Test
        @DisplayName("없는 사용자로 출석을 등록하면 USER_NOT_FOUND 로 거절한다")
        void rejectsUnknownUser() {
            assertMissionError(MissionErrorCase.USER_NOT_FOUND,
                    () -> missionLogService.registerLoginMission(999_999L));
        }
    }

    @Nested
    @DisplayName("문제 작성 미션")
    class ProblemWriteMission {

        @Test
        @DisplayName("하루 세 번까지 보상을 준다")
        void grantsUpToThreeTimesPerDay() {
            for (int i = 0; i < 5; i++) {
                missionLogService.registerProblemWriteMission(user.getId());
            }

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("네 번째부터는 기록도 남기지 않는다")
                    .hasSize(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isEqualTo(30L);
        }

        @Test
        @DisplayName("한 번에 여러 문제를 등록해도 하루 상한인 3건까지만 보상한다")
        void batchStopsAtDailyLimit() {
            missionLogService.registerProblemWriteMissionBatch(user.getId(), 10);

            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isEqualTo(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isEqualTo(30L);
        }

        @ParameterizedTest(name = "이미 {0}건 작성한 상태에서 {1}건 일괄 등록 → 총 {2}건")
        @CsvSource({
                "0, 1, 1",
                "0, 3, 3",
                "0, 7, 3",
                "1, 1, 2",
                "2, 5, 3",
                "3, 5, 3"
        })
        @DisplayName("이미 작성한 건수를 빼고 남은 만큼만 채운다")
        void fillsOnlyRemainingQuota(int existing, int requested, int expectedTotal) {
            for (int i = 0; i < existing; i++) {
                saveMissionLog(user, MissionType.PROBLEM_WRITE, null);
            }

            missionLogService.registerProblemWriteMissionBatch(user.getId(), requested);

            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isEqualTo(expectedTotal);
        }

        @Test
        @DisplayName("0건 또는 음수 건수를 넘기면 아무 것도 만들지 않는다")
        void createsNothingForNonPositiveCount() {
            missionLogService.registerProblemWriteMissionBatch(user.getId(), 0);
            missionLogService.registerProblemWriteMissionBatch(user.getId(), -5);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("상한을 이미 채운 뒤 일괄 등록을 호출해도 기록이 늘지 않는다")
        void addsNothingWhenQuotaIsFull() {
            for (int i = 0; i < 3; i++) {
                saveMissionLog(user, MissionType.PROBLEM_WRITE, null);
            }

            assertThatCode(() -> missionLogService.registerProblemWriteMissionBatch(user.getId(), 5))
                    .doesNotThrowAnyException();
            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isEqualTo(3);
        }

        @Test
        @DisplayName("없는 사용자로 일괄 등록하면 USER_NOT_FOUND 로 거절한다")
        void rejectsUnknownUser() {
            assertMissionError(MissionErrorCase.USER_NOT_FOUND,
                    () -> missionLogService.registerProblemWriteMissionBatch(999_999L, 1));
        }
    }

    @Nested
    @DisplayName("복습 미션")
    class PracticeMission {

        @Test
        @DisplayName("같은 문제는 하루에 한 번만 보상한다")
        void grantsProblemPracticeOncePerDay() {
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
            assertThat(statusOf(user)).satisfies(status -> {
                assertThat(accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint()))
                        .isEqualTo(5L);
                assertThat(status.getTotalStudyPoint()).isEqualTo(5L);
            });
        }

        @Test
        @DisplayName("다른 문제를 복습하면 각각 보상한다")
        void grantsPerProblem() {
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerProblemPracticeMission(user.getId(), 101L);
            missionLogService.registerProblemPracticeMission(user.getId(), 102L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isEqualTo(15L);
        }

        @Test
        @DisplayName("복습노트도 하루에 한 번만 보상한다")
        void grantsNotePracticeOncePerDay() {
            missionLogService.registerNotePracticeMission(user.getId(), 200L);
            missionLogService.registerNotePracticeMission(user.getId(), 200L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
            assertThat(accumulatedNotePracticePoints(user)).isEqualTo(15L);
        }

        @Test
        @DisplayName("어제 복습한 문제는 오늘 다시 보상한다")
        void grantsAgainOnNewDay() {
            saveMissionLogAt(user, MissionType.PROBLEM_PRACTICE, 100L, today().minusDays(1).atTime(9, 0));

            missionLogService.registerProblemPracticeMission(user.getId(), 100L);

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("어제 기록 1건 + 오늘 새로 받은 1건")
                    .hasSize(2);
            assertThat(accumulatedPoints(
                    statusOf(user).getProblemPracticeLevel(),
                    statusOf(user).getProblemPracticePoint()))
                    .as("어제 기록은 경험치를 다시 주지 않는다")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("없는 사용자로 복습을 등록하면 USER_NOT_FOUND 로 거절한다")
        void rejectsUnknownUser() {
            assertMissionError(MissionErrorCase.USER_NOT_FOUND,
                    () -> missionLogService.registerProblemPracticeMission(999_999L, 100L));
            assertMissionError(MissionErrorCase.USER_NOT_FOUND,
                    () -> missionLogService.registerNotePracticeMission(999_999L, 200L));
        }
    }

    @Nested
    @DisplayName("하루 포인트 상한")
    class DailyPointLimit {

        @Test
        @DisplayName("하루에 받을 수 있는 경험치는 200점까지다")
        void capsDailyGainAtTwoHundred() {
            for (int i = 1; i <= 13; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            assertThat(accumulatedNotePracticePoints(user))
                    .as("15점짜리 미션을 13번 하면 12번은 15점씩, 마지막은 남은 5점만 받아 185점이다")
                    .isEqualTo(185L);
        }

        @Test
        @DisplayName("상한을 넘긴 뒤의 미션은 기록만 남고 경험치는 0이다")
        void grantsNothingBeyondLimit() {
            for (int i = 1; i <= 13; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }
            long beforeExtra = accumulatedNotePracticePoints(user);

            missionLogService.registerNotePracticeMission(user.getId(), 14L);

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("보상이 0이어도 수행 기록 자체는 남아야 한다")
                    .hasSize(14);
            assertThat(accumulatedNotePracticePoints(user))
                    .as("상한을 넘겨 경험치가 계속 들어오면 하루 만에 만렙이 된다")
                    .isEqualTo(beforeExtra);
        }

        @Test
        @DisplayName("상한은 사용자마다 따로 적용된다")
        void appliesLimitPerUser() {
            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            missionLogService.registerNotePracticeMission(other.getId(), 100L);

            assertThat(accumulatedNotePracticePoints(other))
                    .as("남이 상한을 채웠다고 내 보상이 막히면 안 된다")
                    .isEqualTo(15L);
        }

        @Test
        @DisplayName("어제 채운 상한은 오늘 다시 열린다")
        void resetsLimitNextDay() {
            for (int i = 1; i <= 14; i++) {
                saveMissionLogAt(user, MissionType.NOTE_PRACTICE, (long) i, today().minusDays(1).atTime(12, 0));
            }

            missionLogService.registerNotePracticeMission(user.getId(), 999L);

            assertThat(accumulatedNotePracticePoints(user))
                    .as("어제 소진한 상한이 오늘까지 이어지면 보상이 영영 막힌다")
                    .isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("관리자 통계 조회")
    class AdminQuery {

        @Test
        @DisplayName("본인 미션 기록만 돌려준다")
        void findsOnlyOwnMissionLogs() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            List<MissionLog> logs = missionLogService.findAllByUserId(user.getId());

            assertThat(logs).singleElement()
                    .satisfies(log -> assertThat(log.getUser().getId()).isEqualTo(user.getId()));
        }

        @Test
        @DisplayName("복습 기록 화면은 NOTE_PRACTICE 만 모아 복습노트 제목과 함께 보여준다")
        void findsAdminPracticeLogsWithNoteTitle() {
            PracticeNote note = savePracticeNote(user.getId(), "미적분 복습");
            saveMissionLog(user, MissionType.NOTE_PRACTICE, note.getId());
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            Page<AdminPracticeLogResponseDto> page = missionLogService.findAdminPracticeLogs(0, 20);

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).singleElement().satisfies(dto -> {
                assertThat(dto.userId()).isEqualTo(user.getId());
                assertThat(dto.practiceTitle()).isEqualTo("미적분 복습");
                assertThat(dto.point()).isEqualTo(15L);
            });
        }

        @Test
        @DisplayName("기록이 없으면 빈 페이지를 준다")
        void returnsEmptyPageWhenNoLog() {
            Page<AdminPracticeLogResponseDto> page = missionLogService.findAdminPracticeLogs(0, 20);

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("복습 기록 건수는 NOTE_PRACTICE 만 센다")
        void countsOnlyNotePracticeLogs() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(user, MissionType.PROBLEM_PRACTICE, 1L);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            assertThat(missionLogService.countNotePracticeLogs()).isEqualTo(1);
        }

        @Test
        @DisplayName("일자별 방문 수는 기록이 없는 날도 0으로 채워 내림차순으로 준다")
        void fillsMissingDatesWithZero() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            Map<LocalDate, Long> daily = missionLogService.getDailyVisitCount(today().minusDays(2), today());

            assertThat(daily).hasSize(3);
            assertThat(daily.keySet()).containsExactly(today(), today().minusDays(1), today().minusDays(2));
            assertThat(daily.get(today())).isEqualTo(1L);
            assertThat(daily.get(today().minusDays(1))).isZero();
        }

        @Test
        @DisplayName("일자별 복습 기록 수도 같은 방식으로 채운다")
        void fillsDailyNotePracticeCounts() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(other, MissionType.NOTE_PRACTICE, 2L);

            Map<LocalDate, Long> daily = missionLogService.getDailyNotePracticeLogsCount(today().minusDays(1), today());

            assertThat(daily.get(today())).isEqualTo(2L);
            assertThat(daily.get(today().minusDays(1))).isZero();
        }

        @Test
        @DisplayName("순 방문자 수는 같은 사용자의 반복 로그인을 한 번으로 센다")
        void countsUniqueVisitors() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            assertThat(missionLogService.countUniqueVisitors(today(), today())).isEqualTo(2L);
        }

        @Test
        @DisplayName("데이터가 없으면 집계는 0으로 나온다")
        void returnsZeroAggregatesWhenNoData() {
            assertThat(missionLogService.countNotePracticeLogs()).isZero();
            assertThat(missionLogService.countUniqueVisitors(today().minusDays(6), today())).isZero();
            assertThat(missionLogService.getActiveUsersByDate(today())).isEmpty();
            assertThat(missionLogService.getDailyActiveUsersCount(today().minusDays(6), today()).values())
                    .containsOnly(0L);
        }
    }
}
