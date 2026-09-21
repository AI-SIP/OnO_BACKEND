package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자동 적립을 <b>껐을 때</b>도 행동 기록은 그대로 남는지.
 *
 * <p>흡수가 끝난 상태에서 제일 위험한 지점이다. 지급을 끄면서 {@code mission_log} 저장까지 같이 멎으면
 * DAU·순 방문자·복습 로그가 전부 0 이 된다. 관리자 화면 7곳이 이 테이블 하나를 읽는다.
 * 그래서 "행은 계속 만들어진다"와 "XP 는 받을 때만 들어온다"를 한자리에서 잠근다.
 */
@DisplayName("자동 적립 꺼짐 - 기록 보존")
class MissionLogRetentionTest extends MissionSystemTestSupport {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemSolveService problemSolveService;

    @Autowired
    private PracticeNoteService practiceNoteService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private PracticeNoteRepository practiceNoteRepository;

    private User user;
    private Folder folder;

    @BeforeEach
    void setUpUser() {
        setLegacyAccrual(false);
        user = fixtures.createUser();
        folder = fixtures.createRootFolder(user.getId());
    }

    @Nested
    @DisplayName("실제 행동 경로")
    class RealActionPaths {

        @Test
        @DisplayName("오답노트를 등록하면 기록이 남고 XP 는 오르지 않는다")
        void problemRegistrationLeavesLog() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());

            assertThat(logTypes()).contains(MissionType.PROBLEM_WRITE);
            assertThat(totalPoint()).isZero();
        }

        @Test
        @DisplayName("복습을 기록하면 기록이 남고 XP 는 오르지 않는다")
        void problemSolveLeavesLog() {
            Problem problem = saveProblem();

            problemSolveService.createProblemSolve(new ProblemSolveRegisterDto(
                    problem.getId(), LocalDateTime.now(), AnswerStatus.CORRECT, "회고", null, 60, null),
                    user.getId());

            assertThat(logTypes()).contains(MissionType.PROBLEM_PRACTICE);
            assertThat(totalPoint()).isZero();
        }

        @Test
        @DisplayName("복습 세트를 끝내면 기록이 남고 XP 는 오르지 않는다")
        void practiceNoteCompletionLeavesLog() {
            PracticeNote note = savePracticeNote();

            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            assertThat(logTypes()).contains(MissionType.NOTE_PRACTICE);
            assertThat(totalPoint()).isZero();
        }

        @Test
        @DisplayName("출석하면 기록이 남고 XP 는 오르지 않는다")
        void loginLeavesLog() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(logTypes()).contains(MissionType.USER_LOGIN);
            assertThat(totalPoint()).isZero();
        }

        @Test
        @DisplayName("중복 방지 판정은 그대로 동작한다")
        void duplicateGuardsStillWork() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerProblemPracticeMission(user.getId(), 100L);
            missionLogService.registerNotePracticeMission(user.getId(), 200L);
            missionLogService.registerNotePracticeMission(user.getId(), 200L);
            for (int i = 0; i < 5; i++) {
                missionLogService.registerProblemWriteMission(user.getId());
            }

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("출석 1 + 문제복습 1 + 노트복습 1 + 문제작성 3")
                    .hasSize(6);
        }
    }

    @Nested
    @DisplayName("관리자 통계")
    class AdminStatistics {

        @Test
        @DisplayName("행동을 태우면 관리자 지표가 모두 값을 돌려준다")
        void adminQueriesStillReturnValues() {
            missionLogService.registerLoginMission(user.getId());
            PracticeNote note = savePracticeNote();
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());

            LocalDate today = today();

            assertThat(missionLogService.countUniqueVisitors(today, today))
                    .as("순 방문자")
                    .isEqualTo(1L);
            assertThat(missionLogService.getDailyVisitCount(today, today).get(today))
                    .as("일자별 방문 수")
                    .isEqualTo(1L);
            assertThat(missionLogService.getDailyActiveUsersCount(today, today).get(today))
                    .as("DAU")
                    .isEqualTo(1L);
            assertThat(missionLogService.getActiveUsersByDate(today))
                    .as("활성 사용자 목록")
                    .isNotEmpty();
            assertThat(missionLogService.countNotePracticeLogs())
                    .as("복습 로그 건수")
                    .isEqualTo(1L);
            assertThat(missionLogService.getDailyNotePracticeLogsCount(today, today).get(today))
                    .as("일자별 복습 로그 수")
                    .isEqualTo(1L);
            assertThat(missionLogService.findAllByUserId(user.getId()))
                    .as("사용자별 기록")
                    .isNotEmpty();

            Page<AdminPracticeLogResponseDto> practiceLogs = missionLogService.findAdminPracticeLogs(0, 20);
            assertThat(practiceLogs.getTotalElements()).isEqualTo(1);
            assertThat(practiceLogs.getContent()).singleElement().satisfies(dto ->
                    assertThat(dto.point())
                            .as("point 컬럼은 계속 채운다. 관리자 화면이 이 값을 그대로 읽는다")
                            .isEqualTo(MissionType.NOTE_PRACTICE.getPoint()));
        }
    }

    @Nested
    @DisplayName("XP 지급 경로")
    class RewardPath {

        @Test
        @DisplayName("미션을 받아야만 XP 가 오른다")
        void onlyClaimGrantsPoint() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());
            assertThat(totalPoint())
                    .as("오답노트를 써도 자동으로 들어오는 XP 는 없다")
                    .isZero();

            MissionProgress progress = progressOf(user, DAILY_NOTE_WRITE);
            assertThat(progress.getCompletedAt()).as("진행도는 그대로 오른다").isNotNull();

            missionService.claim(user.getId(), progress.getId());

            assertThat(totalPoint())
                    .as("미션 화면이 보여준 +10 이 실제 증가량과 같아야 한다")
                    .isEqualTo(10L);
        }
    }

    private List<MissionType> logTypes() {
        return missionLogRepository.findAllByUserId(user.getId()).stream()
                .map(MissionLog::getMissionType)
                .toList();
    }

    private long totalPoint() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return status.getTotalStudyPoint()
                + 5L * status.getTotalStudyLevel() * (status.getTotalStudyLevel() - 1) * 4;
    }

    private Problem saveProblem() {
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                user.getId());
        problem.updateFolder(folder);
        return problemRepository.save(problem);
    }

    private PracticeNote savePracticeNote() {
        return practiceNoteRepository.save(PracticeNote.from(
                new PracticeNoteRegisterDto(null, "복습 세트", List.of(), null), user.getId()));
    }
}
