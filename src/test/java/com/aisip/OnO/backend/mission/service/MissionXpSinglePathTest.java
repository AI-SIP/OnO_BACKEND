package com.aisip.OnO.backend.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.service.LearningCalendarService;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import com.aisip.OnO.backend.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 한 행동에 XP 가 들어오는 길은 정확히 하나다.
 *
 * <p>자동 적립과 미션 진행도가 같은 요청에서 함께 돌면, 적립으로 받은 행동이 "완료됐지만 안 받은" 진행도로 남아
 * 새 앱에서 한 번 더 받을 수 있다(#267). 반대로 둘 다 막히면 그 행동으로는 XP 를 받을 길이 없다.
 *
 * <p>그래서 요청 종류(헤더 없음, 구버전, 새 앱, HTTP 요청 밖) × 비상 스위치(켜짐, 꺼짐) 여덟 조합과
 * 적립을 함께 부르는 행동 네 가지를 전부 돌려 <b>적립과 진행도 중 정확히 하나만</b> 움직이는지 고정한다.
 */
@DisplayName("XP 경로는 하나뿐이다")
class MissionXpSinglePathTest extends MissionSystemTestSupport {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemSolveService problemSolveService;

    @Autowired
    private PracticeNoteService practiceNoteService;

    @Autowired
    private LearningCalendarService learningCalendarService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    private User user;
    private Folder folder;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
        folder = fixtures.createRootFolder(user.getId());
    }

    enum AppRequest {
        NO_HEADER, LEGACY_APP, MISSION_CAPABLE_APP, OUTSIDE_HTTP_REQUEST
    }

    enum Activity {
        LOGIN, PROBLEM_REGISTER, SOLVE_CORRECT, PRACTICE_NOTE_COMPLETE
    }

    enum XpPath {
        LEGACY_ACCRUAL, MISSION_PROGRESS
    }

    static Stream<Arguments> everyCombination() {
        return Arrays.stream(AppRequest.values())
                .flatMap(app -> Stream.of(true, false)
                        .flatMap(flag -> Arrays.stream(Activity.values())
                                .map(activity -> Arguments.of(app, flag, activity))));
    }

    @ParameterizedTest(name = "{0}, 비상 스위치 {1}, {2}")
    @MethodSource("everyCombination")
    @DisplayName("요청 종류와 비상 스위치가 무엇이든 적립과 진행도 중 정확히 하나만 움직인다")
    void exactlyOneXpPath(AppRequest app, boolean legacyAccrualEnabled, Activity activity) {
        setLegacyAccrual(legacyAccrualEnabled);
        actAs(app);

        perform(activity);

        boolean accrued = accumulatedXp() > 0;
        boolean progressed = progressRowCount() > 0;
        assertThat(accrued ^ progressed)
                .as("적립 %s, 진행도 %s. 둘 다면 이중 지급, 둘 다 아니면 그 행동으로는 XP 를 못 받는다",
                        accrued, progressed)
                .isTrue();
        assertThat(accrued ? XpPath.LEGACY_ACCRUAL : XpPath.MISSION_PROGRESS)
                .isEqualTo(expectedPath(app, legacyAccrualEnabled));
    }

    @Nested
    @DisplayName("#267 재현: 구버전 앱으로 활동하고 새 앱으로 미션 화면을 열면")
    class SwitchingApps {

        @Test
        @DisplayName("적립으로 받은 활동은 받을 수 있는 미션으로 남지 않는다")
        void legacyActivityLeavesNothingToClaim() {
            requestFromApp(null);
            missionLogService.registerLoginMission(user.getId());
            problemService.registerProblem(problemDto(), user.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), savePracticeNote().getId());
            long accrued = accumulatedXp();

            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            missionLogService.registerLoginMission(user.getId());

            assertThat(accrued).as("구버전 앱은 지금처럼 적립을 받는다").isPositive();
            assertThat(completedUnclaimedCount())
                    .as("같은 날 새 앱으로 다시 접속해도 이미 적립으로 받은 출석은 미션이 되지 않는다")
                    .isZero();
            assertThat(accumulatedXp()).isEqualTo(accrued);
        }

        @Test
        @DisplayName("새 앱에서 새로 한 활동만 미션으로 받는다")
        void onlyNewAppActivityIsClaimable() {
            requestFromApp(null);
            problemService.registerProblem(problemDto(), user.getId());
            long accrued = accumulatedXp();

            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            problemService.registerProblem(problemDto(), user.getId());

            assertThat(accumulatedXp())
                    .as("새 앱 활동은 적립을 받지 않는다")
                    .isEqualTo(accrued);
            MissionProgress progress = progressOf(user, DAILY_NOTE_WRITE);
            assertThat(progress.getCurrentValue())
                    .as("구버전 앱에서 쓴 오답노트는 세지 않는다")
                    .isEqualTo(1);
            assertThat(progress.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("진행도 증가를 직접 부르면")
    class UpdaterGate {

        @Test
        @DisplayName("적립을 함께 부르는 항목은 정확히 다섯 개다")
        void coveredMetricsAreExactlyFive() {
            // MOOD_LOGGED 는 기분 저장이 적립을 부르지 않아 빠진다. 새 항목을 넣을 때 이 목록을 함께 본다.
            assertThat(Arrays.stream(MissionMetric.values()).filter(MissionMetric::isAlsoAccruedByLegacyPath))
                    .containsExactlyInAnyOrder(
                            MissionMetric.LOGIN_DAY,
                            MissionMetric.PROBLEM_CREATED,
                            MissionMetric.SOLVE_RECORDED,
                            MissionMetric.SOLVE_CORRECT,
                            MissionMetric.PRACTICE_NOTE_COMPLETED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(MissionMetric.class)
        @DisplayName("적립이 도는 요청에서는 적립을 함께 부르는 항목만 막힌다")
        void blocksOnlyCoveredMetricsWhenAccruing(MissionMetric metric) {
            requestFromApp(LEGACY_APP_HEADER);

            missionProgressUpdater.increase(user.getId(), metric);

            assertThat(progressRowCount() > 0).isEqualTo(!metric.isAlsoAccruedByLegacyPath());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(MissionMetric.class)
        @DisplayName("새 앱 요청에서는 모든 항목이 오른다")
        void raisesEveryMetricForMissionCapableApp(MissionMetric metric) {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);

            missionProgressUpdater.increase(user.getId(), metric);

            assertThat(progressRowCount()).isPositive();
        }

        @Test
        @DisplayName("기분 저장은 구버전 앱에서도 기분 미션이 오른다")
        void moodStillCountsForLegacyApp() {
            requestFromApp(null);
            LocalDate today = MissionPeriodKey.today();
            Problem problem = saveProblem();
            jdbcTemplate.update("UPDATE problem SET created_at = ? WHERE id = ?", today.atTime(12, 0), problem.getId());

            learningCalendarService.updateMood(
                    user.getId(), new LearningCalendarMoodRequestDto(today, "cool_sunglasses"));

            assertThat(currentOf(user.getId(), DAILY_MOOD))
                    .as("기분 저장은 적립이 없어 막으면 그 미션으로는 XP 를 못 받는다")
                    .isEqualTo(1);
        }
    }

    private XpPath expectedPath(AppRequest app, boolean legacyAccrualEnabled) {
        if (!legacyAccrualEnabled || app == AppRequest.MISSION_CAPABLE_APP) {
            return XpPath.MISSION_PROGRESS;
        }
        return XpPath.LEGACY_ACCRUAL;
    }

    private void actAs(AppRequest app) {
        switch (app) {
            case NO_HEADER -> requestFromApp(null);
            case LEGACY_APP -> requestFromApp(LEGACY_APP_HEADER);
            case MISSION_CAPABLE_APP -> requestFromApp(MISSION_CAPABLE_APP_HEADER);
            case OUTSIDE_HTTP_REQUEST -> RequestContextHolder.resetRequestAttributes();
        }
    }

    private void perform(Activity activity) {
        switch (activity) {
            case LOGIN -> missionLogService.registerLoginMission(user.getId());
            case PROBLEM_REGISTER -> problemService.registerProblem(problemDto(), user.getId());
            case SOLVE_CORRECT -> problemSolveService.createProblemSolve(
                    new ProblemSolveRegisterDto(
                            saveProblem().getId(), LocalDateTime.now(), AnswerStatus.CORRECT, "회고", null, 120, null),
                    user.getId());
            case PRACTICE_NOTE_COMPLETE -> practiceNoteService.addPracticeNoteCount(
                    user.getId(), savePracticeNote().getId());
        }
    }

    private ProblemRegisterDto problemDto() {
        return new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now());
    }

    private Problem saveProblem() {
        Problem problem = Problem.from(problemDto(), user.getId());
        problem.updateFolder(folder);
        return problemRepository.saveAndFlush(problem);
    }

    private PracticeNote savePracticeNote() {
        return practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, "복습 세트", List.of(), null), user.getId()));
    }

    /** 네 능력치에 지금까지 들어간 경험치 합. 어느 능력치로 들어가든 적립이 있었는지만 본다. */
    private long accumulatedXp() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getAttendanceLevel(), status.getAttendancePoint())
                + accumulatedPoints(status.getNoteWriteLevel(), status.getNoteWritePoint())
                + accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint())
                + accumulatedPoints(status.getNotePracticeLevel(), status.getNotePracticePoint());
    }

    private long progressRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mission_progress WHERE user_id = ? AND current_value > 0",
                Long.class, user.getId());
    }

    private long completedUnclaimedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mission_progress WHERE user_id = ? AND completed_at IS NOT NULL AND claimed_at IS NULL",
                Long.class, user.getId());
    }
}
