package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.service.LearningCalendarService;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.practicenote.dto.PracticeNoteRegisterDto;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2BatchDto;
import com.aisip.OnO.backend.problem.dto.ProblemRegisterV2Dto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.problemsolve.dto.ProblemSolveRegisterDto;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.service.ProblemSolveService;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세는 항목 6종이 실제 행동 경로에 붙어 있는지.
 *
 * <p>진행도 증가 자체는 {@code MissionProgressUpdaterTest} 가 본다. 여기서는 "그 호출이 정말
 * 오답노트 등록·복습 기록·복습 세트 완료·기분 저장·출석 경로에 걸려 있는가"만 확인한다.
 * 붙이는 것을 빠뜨리면 미션은 영원히 0 에서 멈추는데, 단위 테스트로는 절대 드러나지 않는다.
 */
@DisplayName("미션 진행도 - 행동 경로 연결")
class MissionProgressHookTest extends MissionSystemTestSupport {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemSolveService problemSolveService;

    @Autowired
    private PracticeNoteService practiceNoteService;

    @Autowired
    private LearningCalendarService learningCalendarService;

    @Autowired
    private MissionLogService missionLogService;

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
    @DisplayName("오답노트 등록")
    class ProblemCreated {

        @Test
        @DisplayName("한 장 등록하면 오늘의 오답이 1 이 된다")
        void singleRegistration() {
            problemService.registerProblem(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());

            assertThat(currentOf(user.getId(), DAILY_NOTE_WRITE)).isEqualTo(1);
            assertThat(currentOf(user.getId(), WEEKLY_NOTE_10)).isEqualTo(1);
        }

        @Test
        @DisplayName("세 장을 한 번에 등록하면 주간 미션이 3 이 된다")
        void batchRegistrationCountsEveryProblem() {
            List<ProblemRegisterV2Dto> problems = List.of(
                    v2Dto("메모1"), v2Dto("메모2"), v2Dto("메모3"));

            problemService.registerProblemsV2(new ProblemRegisterV2BatchDto(problems), user.getId());

            assertThat(currentOf(user.getId(), WEEKLY_NOTE_10))
                    .as("여러 장 등록은 장수만큼 오른다")
                    .isEqualTo(3);
            assertThat(currentOf(user.getId(), DAILY_NOTE_WRITE))
                    .as("일일 미션은 목표 1 에서 멈춘다")
                    .isEqualTo(1);
        }

        private ProblemRegisterV2Dto v2Dto(String memo) {
            return new ProblemRegisterV2Dto(
                    null, memo, "출처", folder.getId(), LocalDateTime.now(), List.of(), List.of());
        }
    }

    @Nested
    @DisplayName("복습 기록")
    class SolveRecorded {

        @Test
        @DisplayName("복습을 기록하면 복습 미션이 오른다")
        void recordsReview() {
            problemSolveService.createProblemSolve(solveDto(saveProblem(), AnswerStatus.WRONG), user.getId());

            assertThat(currentOf(user.getId(), DAILY_REVIEW_3)).isEqualTo(1);
            assertThat(currentOf(user.getId(), WEEKLY_REVIEW_30)).isEqualTo(1);
        }

        @Test
        @DisplayName("정답이면 정확도 미션도 함께 오른다")
        void countsCorrectAnswer() {
            problemSolveService.createProblemSolve(solveDto(saveProblem(), AnswerStatus.CORRECT), user.getId());

            assertThat(currentOf(user.getId(), DAILY_REVIEW_3)).isEqualTo(1);
            assertThat(currentOf(user.getId(), DAILY_CORRECT_3)).isEqualTo(1);
        }

        @Test
        @DisplayName("오답이면 정확도 미션은 오르지 않는다")
        void doesNotCountWrongAnswer() {
            problemSolveService.createProblemSolve(solveDto(saveProblem(), AnswerStatus.WRONG), user.getId());

            assertThat(currentOf(user.getId(), DAILY_CORRECT_3)).isZero();
        }

        private Problem saveProblem() {
            Problem problem = Problem.from(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());
            problem.updateFolder(folder);
            return problemRepository.save(problem);
        }

        private ProblemSolveRegisterDto solveDto(Problem problem, AnswerStatus answerStatus) {
            return new ProblemSolveRegisterDto(
                    problem.getId(), LocalDateTime.now(), answerStatus, "회고", null, 120, null);
        }
    }

    @Nested
    @DisplayName("복습 세트 완료")
    class PracticeNoteCompleted {

        @Test
        @DisplayName("복습 세트를 끝내면 세트 미션이 오른다")
        void countsCompletion() {
            PracticeNote practiceNote = practiceNoteRepository.save(PracticeNote.from(
                    new PracticeNoteRegisterDto(null, "복습 세트", List.of(), null), user.getId()));

            practiceNoteService.addPracticeNoteCount(user.getId(), practiceNote.getId());

            assertThat(currentOf(user.getId(), DAILY_PRACTICE_SET)).isEqualTo(1);
            assertThat(currentOf(user.getId(), WEEKLY_SET_3)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("학습 달력 기분")
    class MoodLogged {

        @Test
        @DisplayName("기분을 처음 남기면 기분 미션이 오른다")
        void countsFirstMood() {
            LocalDate today = studyDay();

            learningCalendarService.updateMood(
                    user.getId(), new LearningCalendarMoodRequestDto(today, "cool_sunglasses"));

            assertThat(currentOf(user.getId(), DAILY_MOOD)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날 기분을 바꿔도 두 번 오르지 않는다")
        void doesNotCountMoodChange() {
            LocalDate today = studyDay();
            learningCalendarService.updateMood(
                    user.getId(), new LearningCalendarMoodRequestDto(today, "cool_sunglasses"));

            learningCalendarService.updateMood(
                    user.getId(), new LearningCalendarMoodRequestDto(today, "happy_tears"));

            assertThat(currentOf(user.getId(), DAILY_MOOD))
                    .as("이모지를 바꿀 때마다 오르면 버튼 한 번으로 미션을 채울 수 있다")
                    .isEqualTo(1);
        }

        /** 기분은 학습 기록이 있는 날에만 남길 수 있다. 오늘 작성한 오답노트로 그 조건을 만든다. */
        private LocalDate studyDay() {
            Problem problem = Problem.from(
                    new ProblemRegisterDto(null, "메모", "출처", folder.getId(), LocalDateTime.now()),
                    user.getId());
            problem.updateFolder(folder);
            problemRepository.saveAndFlush(problem);
            return problemRepository.findById(problem.getId()).orElseThrow().getCreatedAt().toLocalDate();
        }
    }

    @Nested
    @DisplayName("출석")
    class LoginDay {

        @Test
        @DisplayName("오늘 처음 접속하면 출석 미션이 오른다")
        void countsFirstLogin() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isEqualTo(1);
            assertThat(currentOf(user.getId(), WEEKLY_ATTEND_5)).isEqualTo(1);
        }

        @Test
        @DisplayName("하루에 여러 번 접속해도 한 번만 오른다")
        void countsOncePerDay() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());

            assertThat(currentOf(user.getId(), WEEKLY_ATTEND_5))
                    .as("앱을 열 때마다 오르면 주간 출석 5일이 하루 만에 채워진다")
                    .isEqualTo(1);
        }
    }
}
