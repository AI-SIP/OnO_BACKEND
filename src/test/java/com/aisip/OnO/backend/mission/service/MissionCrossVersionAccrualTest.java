package com.aisip.OnO.backend.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aisip.OnO.backend.folder.entity.Folder;
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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 계정을 신버전과 구버전 기기에서 번갈아 쓸 때(#318).
 *
 * <p>신버전 요청도 {@code mission_log} 행을 남긴다. 관리자 통계 일곱 곳과 훈장 '개근'이 이 테이블만 보기 때문에
 * 행을 안 남길 수는 없다({@code MissionLogRetentionTest}). 그런데 중복 방지 판정이 그 행을 그대로 세면,
 * 뒤이어 온 구버전 요청이 "이미 적립했다"로 막힌다. 구버전 요청은 진행도도 올리지 않으므로
 * 그 활동은 적립으로도 진행도로도 남지 않는다.
 *
 * <p>그래서 <b>구버전 요청의 중복 방지는 실제로 적립된 행만 센다.</b> 신버전 요청의 판정은 그대로
 * 모든 행을 세야 한다. 그러지 않으면 신버전 단독 사용자가 앱을 열 때마다 출석 진행도가 다시 오른다.
 * 두 방향을 한자리에서 잠근다.
 */
@DisplayName("신·구버전 기기를 번갈아 써도")
class MissionCrossVersionAccrualTest extends MissionSystemTestSupport {

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
        user = fixtures.createUser();
        folder = fixtures.createRootFolder(user.getId());
    }

    @Nested
    @DisplayName("신버전으로 먼저 활동한 뒤 구버전으로 같은 활동을 하면")
    class LegacyAfterMissionCapable {

        @Test
        @DisplayName("출석 XP 를 받는다")
        void loginStillAccrues() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            missionLogService.registerLoginMission(user.getId());

            requestFromApp(null);
            missionLogService.registerLoginMission(user.getId());

            assertThat(attendanceXp())
                    .as("신버전이 남긴 출석 기록 때문에 구버전 출석이 통째로 사라지면 안 된다")
                    .isEqualTo(MissionType.USER_LOGIN.getPoint());
        }

        @Test
        @DisplayName("오답노트 등록 XP 를 받는다")
        void problemWriteStillAccrues() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            for (int i = 0; i < 3; i++) {
                problemService.registerProblem(problemDto(), user.getId());
            }

            requestFromApp(null);
            problemService.registerProblem(problemDto(), user.getId());

            assertThat(noteWriteXp())
                    .as("하루 3건 한도를 신버전 활동이 대신 소진하면 안 된다")
                    .isEqualTo(MissionType.PROBLEM_WRITE.getPoint());
        }

        @Test
        @DisplayName("복습 기록 XP 를 받는다")
        void problemPracticeStillAccrues() {
            Problem problem = saveProblem();

            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            problemSolveService.createProblemSolve(solveDto(problem), user.getId());

            requestFromApp(null);
            problemSolveService.createProblemSolve(solveDto(problem), user.getId());

            assertThat(problemPracticeXp())
                    .as("같은 문제를 신버전이 먼저 건드렸다고 구버전 복습이 사라지면 안 된다")
                    .isEqualTo(MissionType.PROBLEM_PRACTICE.getPoint());
        }

        @Test
        @DisplayName("세트 완료 XP 를 받는다")
        void notePracticeStillAccrues() {
            PracticeNote note = savePracticeNote();

            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            requestFromApp(null);
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            assertThat(notePracticeXp())
                    .as("같은 세트를 신버전이 먼저 끝냈다고 구버전 완료가 사라지면 안 된다")
                    .isEqualTo(MissionType.NOTE_PRACTICE.getPoint());
        }

        @Test
        @DisplayName("신버전 활동은 하루 200점 상한을 갉아먹지 않는다")
        void missionCapableActivityDoesNotFillDailyLimit() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            // 정가로 세면 15 * 14 = 210 점이라 하루 상한 200 을 넘긴다.
            for (int i = 0; i < 14; i++) {
                practiceNoteService.addPracticeNoteCount(user.getId(), savePracticeNote().getId());
            }

            requestFromApp(null);
            missionLogService.registerLoginMission(user.getId());

            assertThat(attendanceXp())
                    .as("지급되지 않은 신버전 활동이 상한을 채워 구버전 적립을 막으면 안 된다")
                    .isEqualTo(MissionType.USER_LOGIN.getPoint());
        }

        @Test
        @DisplayName("신버전이 남긴 기록은 그대로 남는다")
        void missionCapableLogRowsRemain() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            missionLogService.registerLoginMission(user.getId());

            requestFromApp(null);
            missionLogService.registerLoginMission(user.getId());

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("관리자 통계와 훈장이 이 행만 본다. 신버전 행을 지우면 지표가 통째로 0 이 된다")
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("신버전만 쓰는 사용자는")
    class MissionCapableOnly {

        @Test
        @DisplayName("앱을 여러 번 열어도 출석 진행도가 한 번만 오른다")
        void loginProgressRisesOnce() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);

            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());

            assertThat(currentOf(user.getId(), WEEKLY_ATTEND_5))
                    .as("같은 날 다시 접속한 것으로 주간 출석이 차면 안 된다")
                    .isEqualTo(1);
            assertThat(attendanceXp()).as("신버전은 적립을 받지 않는다").isZero();
        }

        @Test
        @DisplayName("같은 세트를 반복 완료해도 세트 진행도가 한 번만 오른다")
        void practiceNoteProgressRisesOnce() {
            PracticeNote note = savePracticeNote();
            requestFromApp(MISSION_CAPABLE_APP_HEADER);

            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            assertThat(currentOf(user.getId(), WEEKLY_SET_3))
                    .as("같은 세트를 세 번 완료한 것으로 주간 세트 미션이 채워지면 안 된다")
                    .isEqualTo(1);
            assertThat(notePracticeXp()).as("신버전은 적립을 받지 않는다").isZero();
        }

        @Test
        @DisplayName("오답노트 기록은 하루 3건까지만 남는다")
        void problemWriteLogsStopAtThree() {
            requestFromApp(MISSION_CAPABLE_APP_HEADER);

            for (int i = 0; i < 5; i++) {
                problemService.registerProblem(problemDto(), user.getId());
            }

            assertThat(missionLogRepository.countProblemWritesToday(user.getId()))
                    .as("자동 적립 한도 판정은 신버전 요청에서 지금과 같아야 한다")
                    .isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("구버전만 쓰는 사용자는")
    class LegacyOnly {

        @Test
        @DisplayName("같은 활동을 반복해도 지금처럼 한 번만 적립된다")
        void accruesOnlyOnce() {
            requestFromApp(null);
            PracticeNote note = savePracticeNote();
            Problem problem = saveProblem();

            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            problemSolveService.createProblemSolve(solveDto(problem), user.getId());
            problemSolveService.createProblemSolve(solveDto(problem), user.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());
            for (int i = 0; i < 5; i++) {
                problemService.registerProblem(problemDto(), user.getId());
            }

            assertThat(attendanceXp()).isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(problemPracticeXp()).isEqualTo(MissionType.PROBLEM_PRACTICE.getPoint());
            assertThat(notePracticeXp()).isEqualTo(MissionType.NOTE_PRACTICE.getPoint());
            assertThat(noteWriteXp())
                    .as("오답노트는 하루 3건까지만 적립된다")
                    .isEqualTo(3 * MissionType.PROBLEM_WRITE.getPoint());
            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("출석 1 + 문제복습 1 + 세트 1 + 오답노트 3")
                    .hasSize(6);
        }
    }

    @Nested
    @DisplayName("구버전으로 먼저 적립한 뒤 신버전으로 같은 활동을 하면")
    class MissionCapableAfterLegacy {

        @Test
        @DisplayName("진행도는 오르지 않는다")
        void progressStaysBlocked() {
            requestFromApp(null);
            PracticeNote note = savePracticeNote();
            missionLogService.registerLoginMission(user.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            requestFromApp(MISSION_CAPABLE_APP_HEADER);
            missionLogService.registerLoginMission(user.getId());
            practiceNoteService.addPracticeNoteCount(user.getId(), note.getId());

            assertThat(currentOf(user.getId(), WEEKLY_ATTEND_5))
                    .as("이미 적립으로 받은 출석을 미션으로 한 번 더 받으면 안 된다")
                    .isZero();
            assertThat(currentOf(user.getId(), WEEKLY_SET_3))
                    .as("이미 적립으로 받은 세트 완료를 미션으로 한 번 더 받으면 안 된다")
                    .isZero();
        }
    }

    private long attendanceXp() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getAttendanceLevel(), status.getAttendancePoint());
    }

    private long noteWriteXp() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getNoteWriteLevel(), status.getNoteWritePoint());
    }

    private long problemPracticeXp() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint());
    }

    private long notePracticeXp() {
        UserMissionStatus status = reload(user).getUserMissionStatus();
        return accumulatedPoints(status.getNotePracticeLevel(), status.getNotePracticePoint());
    }

    private ProblemRegisterDto problemDto() {
        return new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now());
    }

    private ProblemSolveRegisterDto solveDto(Problem problem) {
        return new ProblemSolveRegisterDto(
                problem.getId(), LocalDateTime.now(), AnswerStatus.CORRECT, "회고", null, 120, null);
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
}
