package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MissionLogService")
class MissionLogServiceTest extends MissionTestSupport {

    @Autowired
    private MissionLogService missionLogService;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
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
        @DisplayName("첫 로그인이면 기록을 남긴다 - 경험치는 주지 않는다")
        void recordsFirstLoginWithoutGrantingPoint() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("DAU·순 방문자 집계가 이 행을 읽는다. 행이 사라지면 지표가 통째로 0 이 된다")
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.getMissionType()).isEqualTo(MissionType.USER_LOGIN);
                        assertThat(log.getPoint())
                                .as("point 컬럼은 그대로 채운다. 집계와 관리자 화면이 읽는 값이다")
                                .isEqualTo(15L);
                    });
            assertThat(statusOf(user)).satisfies(status -> {
                assertThat(accumulatedPoints(status.getAttendanceLevel(), status.getAttendancePoint()))
                        .as("XP 는 미션을 받을 때만 들어온다")
                        .isZero();
                assertThat(status.getTotalStudyPoint()).isZero();
            });
        }

        @Test
        @DisplayName("같은 날 다시 로그인해도 기록이 늘지 않는다")
        void ignoresSecondLoginOfSameDay() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("하루에 여러 번 로그인해도 출석은 한 번이다")
                    .hasSize(1);
            assertThat(statusOf(user).getTotalStudyPoint()).isZero();
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
            assertThat(missionLogRepository.findAllByUserId(other.getId())).hasSize(1);
            assertThat(statusOf(user).getTotalStudyPoint()).isZero();
            assertThat(statusOf(other).getTotalStudyPoint()).isZero();
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
        @DisplayName("하루 세 번까지 기록을 남긴다")
        void recordsUpToThreeTimesPerDay() {
            for (int i = 0; i < 5; i++) {
                missionLogService.registerProblemWriteMission(user.getId());
            }

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("네 번째부터는 기록도 남기지 않는다")
                    .hasSize(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("한 번에 여러 문제를 등록해도 하루 상한인 3건까지만 기록한다")
        void batchStopsAtDailyLimit() {
            missionLogService.registerProblemWriteMissionBatch(user.getId(), 10);

            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isEqualTo(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isZero();
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
        @DisplayName("같은 문제는 하루에 한 번만 기록한다")
        void recordsProblemPracticeOncePerDay() {
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
            assertThat(statusOf(user)).satisfies(status -> {
                assertThat(accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint()))
                        .isZero();
                assertThat(status.getTotalStudyPoint()).isZero();
            });
        }

        @Test
        @DisplayName("다른 문제를 복습하면 각각 기록한다")
        void recordsPerProblem() {
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerProblemPracticeMission(user.getId(), 101L);
            missionLogService.registerProblemPracticeMission(user.getId(), 102L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(3);
            assertThat(statusOf(user).getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("복습노트도 하루에 한 번만 기록한다")
        void recordsNotePracticeOncePerDay() {
            missionLogService.registerNotePracticeMission(user.getId(), 200L);
            missionLogService.registerNotePracticeMission(user.getId(), 200L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
            assertThat(accumulatedNotePracticePoints(user)).isZero();
        }

        @Test
        @DisplayName("어제 복습한 문제는 오늘 다시 보상한다")
        void grantsAgainOnNewDay() {
            saveMissionLogAt(user, MissionType.PROBLEM_PRACTICE, 100L, today().minusDays(1).atTime(9, 0));

            missionLogService.registerProblemPracticeMission(user.getId(), 100L);

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("어제 기록 1건 + 오늘 새로 남긴 1건")
                    .hasSize(2);
            assertThat(accumulatedPoints(
                    statusOf(user).getProblemPracticeLevel(),
                    statusOf(user).getProblemPracticePoint()))
                    .as("기록을 남겨도 경험치는 들어오지 않는다")
                    .isZero();
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
    @DisplayName("일반 미션 등록 진입점")
    class RegisterMissionLog {

        @ParameterizedTest
        @EnumSource(MissionType.class)
        @DisplayName("모든 미션 종류를 등록할 수 있고 항상 0을 돌려준다")
        void registersEveryMissionType(MissionType missionType) {
            Long returned = missionLogService.registerMissionLog(MissionRegisterDto.builder()
                    .userId(user.getId())
                    .missionType(missionType)
                    .referenceId(300L)
                    .build());

            assertThat(returned)
                    .as("획득 포인트를 돌려주는 것처럼 보이지만 실제로는 항상 0이다")
                    .isEqualTo(0L);
            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .singleElement()
                    .satisfies(log -> assertThat(log.getMissionType()).isEqualTo(missionType));
        }

        @Test
        @DisplayName("이미 수행한 미션은 다시 등록되지 않는다")
        void skipsAlreadyDoneMission() {
            MissionRegisterDto dto = MissionRegisterDto.builder()
                    .userId(user.getId())
                    .missionType(MissionType.USER_LOGIN)
                    .build();

            missionLogService.registerMissionLog(dto);
            missionLogService.registerMissionLog(dto);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(1);
        }

        @Test
        @DisplayName("미션 종류가 없으면 MISSION_TYPE_NOT_FOUND 로 거절한다")
        void rejectsNullMissionType() {
            MissionRegisterDto dto = MissionRegisterDto.builder()
                    .userId(user.getId())
                    .build();

            assertMissionError(MissionErrorCase.MISSION_TYPE_NOT_FOUND,
                    () -> missionLogService.registerMissionLog(dto));
        }

        @Test
        @DisplayName("없는 사용자로 등록하면 USER_NOT_FOUND 로 거절한다")
        void rejectsUnknownUser() {
            MissionRegisterDto dto = MissionRegisterDto.builder()
                    .userId(999_999L)
                    .missionType(MissionType.USER_LOGIN)
                    .build();

            assertMissionError(MissionErrorCase.USER_NOT_FOUND,
                    () -> missionLogService.registerMissionLog(dto));
        }
    }

    /**
     * 예전에는 이 자리에 "하루 200점 상한" 테스트가 있었다. 상한은 자동 적립을 막기 위한 장치였는데
     * 자동 적립 자체가 없어져 막을 대상이 사라졌다. 같은 시나리오를 새 동작으로 옮겨 둔다.
     * 지키려던 것("하루 만에 만렙이 되지 않는다")은 미션 설계가 대신한다. 일일 6종을 다 받아도 75 XP 다.
     */
    @Nested
    @DisplayName("자동 적립 폐지")
    class NoAutomaticPointGrant {

        @Test
        @DisplayName("몇 번을 해도 경험치는 오르지 않는다")
        void neverGrantsPointNoMatterHowManyTimes() {
            for (int i = 1; i <= 13; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            assertThat(accumulatedNotePracticePoints(user))
                    .as("행동만으로 경험치가 들어오면 미션 화면의 숫자와 실제 증가량이 어긋난다")
                    .isZero();
        }

        @Test
        @DisplayName("경험치를 주지 않아도 수행 기록은 전부 남는다")
        void keepsEveryLogWithoutPoint() {
            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("관리자 통계가 이 행을 읽는다. 지급을 걷어내면서 기록까지 없애면 지표가 죽는다")
                    .hasSize(14);
            assertThat(accumulatedNotePracticePoints(user)).isZero();
        }

        @Test
        @DisplayName("사용자마다 기록은 따로 쌓이고 경험치는 둘 다 0이다")
        void recordsPerUser() {
            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            missionLogService.registerNotePracticeMission(other.getId(), 100L);

            assertThat(missionLogRepository.findAllByUserId(other.getId())).hasSize(1);
            assertThat(accumulatedNotePracticePoints(other)).isZero();
            assertThat(accumulatedNotePracticePoints(user)).isZero();
        }

        @Test
        @DisplayName("어제 기록이 많아도 오늘 기록은 그대로 남는다")
        void stillRecordsNextDay() {
            for (int i = 1; i <= 14; i++) {
                saveMissionLogAt(user, MissionType.NOTE_PRACTICE, (long) i, today().minusDays(1).atTime(12, 0));
            }

            missionLogService.registerNotePracticeMission(user.getId(), 999L);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).hasSize(15);
            assertThat(accumulatedNotePracticePoints(user)).isZero();
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
