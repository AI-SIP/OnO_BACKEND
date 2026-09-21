package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.dto.AdminStatsDto;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.AnalysisStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LearningStats;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.UserStats;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminAnalysisController")
class AdminAnalysisControllerTest extends AdminTestSupport {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @BeforeEach
    void loginAsAdmin() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private Map<LocalDate, Long> dailyMap(MvcResult result, String name) {
        return (Map<LocalDate, Long>) result.getModelAndView().getModel().get(name);
    }

    private <T> T attr(MvcResult result, String name, Class<T> type) {
        return type.cast(result.getModelAndView().getModel().get(name));
    }

    private MvcResult analysis(LocalDate start, LocalDate end) throws Exception {
        return mockMvc.perform(get("/admin/analysis")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ProblemSolve saveSolve(User user, AnswerStatus answerStatus, LocalDateTime practicedAt) {
        Problem problem = saveProblem(user.getId(), fixtures.createRootFolder(user.getId()), "메모");
        return problemSolveRepository.save(ProblemSolve.create(
                problem, user.getId(), practicedAt, answerStatus, "다시 풀었다", null, 90, null));
    }

    @Nested
    @DisplayName("종합 통계")
    class Overview {

        @Test
        @DisplayName("집계 대상 데이터가 없어도 500이 아니라 0으로 채운 화면을 준다")
        void rendersZeroesWhenNoDataExists() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/analysis"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("analysis"))
                    .andReturn();

            LearningStats learning = attr(result, "learningStats", LearningStats.class);
            UserStats users = attr(result, "userStats", UserStats.class);
            AnalysisStats analysis = attr(result, "analysisStats", AnalysisStats.class);

            assertThat(learning.totalProblems()).isZero();
            assertThat(learning.totalPracticeNotes()).isZero();
            assertThat(learning.solves().value()).isZero();
            assertThat(learning.problems().deltaPercent())
                    .as("직전 기간이 0 이면 증감률을 계산하지 않는다(0 으로 나누지 않는다)")
                    .isNull();
            assertThat(users.activeUsers().value()).isZero();
            assertThat(users.averageDau().value()).isZero();
            assertThat(analysis.allTotal()).isZero();
            assertThat(analysis.periodFailureRate()).isZero();
            assertThat(dailyMap(result, "dailyActiveUsers").values())
                    .as("데이터가 없는 날짜도 비워두지 말고 0으로 채워야 그래프가 끊기지 않는다")
                    .containsOnly(0L);
        }

        @Test
        @DisplayName("기간을 지정하지 않으면 최근 30일을 보고, 직전 비교 기간도 30일이다")
        void defaultsToLastThirtyDays() throws Exception {
            mockMvc.perform(get("/admin/analysis"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("startDate", TODAY.minusDays(29)))
                    .andExpect(model().attribute("endDate", TODAY))
                    .andExpect(model().attribute("days", 30L))
                    .andExpect(model().attribute("previousStartDate", TODAY.minusDays(59)))
                    .andExpect(model().attribute("previousEndDate", TODAY.minusDays(30)))
                    .andExpect(model().attribute("quickStart7Days", TODAY.minusDays(6)))
                    .andExpect(model().attribute("quickStart30Days", TODAY.minusDays(29)))
                    .andExpect(model().attribute("quickStart90Days", TODAY.minusDays(89)))
                    .andExpect(model().attribute("quickStartMonth", TODAY.withDayOfMonth(1)));
        }

        @Test
        @DisplayName("로그인한 유저 수가 활성 유저와 평균 DAU 로 집계된다")
        void aggregatesLoginMissionsAsActiveUsers() throws Exception {
            User first = fixtures.createUser();
            User second = fixtures.createUser();
            saveMissionLog(first, MissionType.USER_LOGIN, null);
            saveMissionLog(second, MissionType.USER_LOGIN, null);

            MvcResult result = analysis(TODAY, TODAY);

            UserStats users = attr(result, "userStats", UserStats.class);
            assertThat(users.activeUsers().value()).isEqualTo(2.0);
            assertThat(users.averageDau().value()).isEqualTo(2.0);
            assertThat(dailyMap(result, "dailyActiveUsers").get(TODAY)).isEqualTo(2L);
        }

        @Test
        @DisplayName("같은 사용자가 여러 번 로그인해도 활성 유저는 1명으로 센다")
        void countsRepeatLoginAsSingleActiveUser() throws Exception {
            User user = fixtures.createUser();
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            MvcResult result = analysis(TODAY, TODAY);

            assertThat(attr(result, "userStats", UserStats.class).activeUsers().value()).isEqualTo(1.0);
            assertThat(dailyMap(result, "dailyActiveUsers").get(TODAY)).isEqualTo(1L);
        }

        @Test
        @DisplayName("시작일이 종료일보다 뒤면 두 값을 맞바꿔 처리한다")
        void swapsReversedDateRange() throws Exception {
            mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.toString())
                            .param("endDate", TODAY.minusDays(6).toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("startDate", TODAY.minusDays(6)))
                    .andExpect(model().attribute("endDate", TODAY));
        }

        @Test
        @DisplayName("366일보다 긴 기간은 종료일 기준 366일로 줄인다")
        void clampsTooLongRange() throws Exception {
            mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.minusYears(3).toString())
                            .param("endDate", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("startDate", TODAY.minusDays(365)))
                    .andExpect(model().attribute("days", 366L));
        }

        @Test
        @DisplayName("시작일과 종료일이 같은 하루 조회도 500 없이 처리한다")
        void handlesSingleDayRange() throws Exception {
            User user = fixtures.createUser();
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            MvcResult result = analysis(TODAY, TODAY);

            assertThat(dailyMap(result, "dailyActiveUsers"))
                    .as("하루짜리 조회는 정확히 하루치 항목만 있어야 한다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("기간 밖의 기록은 집계에서 빠진다")
        void excludesRecordsOutsideRange() throws Exception {
            User user = fixtures.createUser();
            var oldLog = saveMissionLog(user, MissionType.USER_LOGIN, null);
            forceCreatedAt("mission_log", oldLog.getId(), TODAY.minusDays(40).atTime(12, 0));

            MvcResult result = analysis(TODAY.minusDays(6), TODAY);

            assertThat(attr(result, "userStats", UserStats.class).activeUsers().value()).isZero();
        }

        @Test
        @DisplayName("날짜 형식이 잘못되면 400으로 거절한다")
        void rejectsMalformedDate() throws Exception {
            mockMvc.perform(get("/admin/analysis").param("startDate", "2026-13-45"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("학습 지표")
    class Learning {

        @Test
        @DisplayName("복습은 복습노트 첫 완료 미션이 아니라 problem_solve 기록을 모두 센다")
        void countsEverySolveAsPractice() throws Exception {
            User user = fixtures.createUser();
            saveSolve(user, AnswerStatus.CORRECT, TODAY.atTime(9, 0));
            saveSolve(user, AnswerStatus.WRONG, TODAY.atTime(10, 0));
            saveSolve(user, AnswerStatus.CORRECT, TODAY.atTime(11, 0));

            MvcResult result = analysis(TODAY, TODAY);

            LearningStats learning = attr(result, "learningStats", LearningStats.class);
            assertThat(learning.solves().value()).isEqualTo(3.0);
            assertThat(learning.solvers()).isEqualTo(1L);
            assertThat(learning.correct()).isEqualTo(2L);
            assertThat(learning.wrong()).isEqualTo(1L);
            assertThat(learning.practiceNoteFirstCompletions())
                    .as("복습노트 첫 완료 미션은 따로 센다")
                    .isZero();
            assertThat(dailyMap(result, "dailySolves").get(TODAY)).isEqualTo(3L);
        }

        @Test
        @DisplayName("복습 기간은 기록이 만들어진 때가 아니라 실제로 푼 시각 기준이다")
        void usesPracticedAtForSolves() throws Exception {
            User user = fixtures.createUser();
            saveSolve(user, AnswerStatus.CORRECT, TODAY.minusDays(20).atTime(9, 0));

            MvcResult result = analysis(TODAY.minusDays(6), TODAY);

            assertThat(attr(result, "learningStats", LearningStats.class).solves().value()).isZero();
        }

        @Test
        @DisplayName("오답노트 등록 수를 직전 같은 길이 기간과 비교한다")
        void comparesProblemsWithPreviousPeriod() throws Exception {
            User user = fixtures.createUser();
            saveProblem(user.getId(), null, "이번");
            saveProblem(user.getId(), null, "이번");
            var old = saveProblem(user.getId(), null, "직전");
            forceCreatedAt("problem", old.getId(), TODAY.minusDays(10).atTime(9, 0));

            MvcResult result = analysis(TODAY.minusDays(6), TODAY);

            LearningStats learning = attr(result, "learningStats", LearningStats.class);
            assertThat(learning.problems().value()).isEqualTo(2.0);
            assertThat(learning.problems().previous()).isEqualTo(1.0);
            assertThat(learning.problems().deltaPercent()).isEqualTo(100.0);
            assertThat(learning.problemWriters()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("재방문")
    class Retention {

        @Test
        @DisplayName("가입 다음 날 로그인한 유저만 D1 재방문으로 센다")
        void countsNextDayLoginAsD1() throws Exception {
            LocalDate joined = TODAY.minusDays(3);
            User returned = fixtures.createUser();
            User left = fixtures.createUser();
            forceCreatedAt("user", returned.getId(), joined.atTime(10, 0));
            forceCreatedAt("user", left.getId(), joined.atTime(11, 0));
            var login = saveMissionLog(returned, MissionType.USER_LOGIN, null);
            forceCreatedAt("mission_log", login.getId(), joined.plusDays(1).atTime(8, 0));

            MvcResult result = analysis(joined, joined);

            AdminStatsDto.Retention d1 = attr(result, "userStats", UserStats.class).d1();
            assertThat(d1.cohort()).isEqualTo(2L);
            assertThat(d1.retained()).isEqualTo(1L);
            assertThat(d1.rate()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("아직 7일이 지나지 않은 가입자는 D7 코호트에서 뺀다")
        void excludesCohortThatHasNotReachedDay7() throws Exception {
            User recent = fixtures.createUser();
            forceCreatedAt("user", recent.getId(), TODAY.minusDays(2).atTime(10, 0));

            MvcResult result = analysis(TODAY.minusDays(6), TODAY);

            assertThat(attr(result, "userStats", UserStats.class).d7().cohort()).isZero();
        }
    }

    @Nested
    @DisplayName("일자별 신규 가입자")
    class DailyNewUsers {

        @Test
        @DisplayName("해당 날짜에 가입한 사용자만 보여준다")
        void showsUsersJoinedOnGivenDate() throws Exception {
            User today = fixtures.createUser();
            User past = fixtures.createUser();
            forceCreatedAt("user", past.getId(), TODAY.minusDays(3).atTime(9, 0));

            MvcResult result = mockMvc.perform(get("/admin/analysis/daily-new-users")
                            .param("date", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("daily-users"))
                    .andExpect(model().attribute("type", "new"))
                    .andExpect(model().attribute("date", TODAY))
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<UserResponseDto> users = (List<UserResponseDto>) result.getModelAndView().getModel().get("users");
            assertThat(users).extracting(UserResponseDto::userId)
                    .contains(today.getId())
                    .doesNotContain(past.getId());
        }

        @Test
        @DisplayName("가입자가 없는 날짜는 빈 목록을 준다")
        void returnsEmptyListForDateWithoutSignups() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/analysis/daily-new-users")
                            .param("date", TODAY.minusYears(5).toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<UserResponseDto> users = (List<UserResponseDto>) result.getModelAndView().getModel().get("users");
            assertThat(users).isEmpty();
        }

        @Test
        @DisplayName("date 파라미터가 없으면 400으로 거절한다")
        void rejectsMissingDate() throws Exception {
            mockMvc.perform(get("/admin/analysis/daily-new-users"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("일자별 출석 유저")
    class DailyActiveUsers {

        @Test
        @DisplayName("로그인 미션을 남긴 사용자만 출석으로 본다")
        void showsOnlyUsersWithLoginMission() throws Exception {
            User active = fixtures.createUser();
            User inactive = fixtures.createUser();
            saveMissionLog(active, MissionType.USER_LOGIN, null);
            saveMissionLog(inactive, MissionType.PROBLEM_WRITE, null);

            MvcResult result = mockMvc.perform(get("/admin/analysis/daily-active-users")
                            .param("date", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("daily-users"))
                    .andExpect(model().attribute("type", "active"))
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<UserResponseDto> users = (List<UserResponseDto>) result.getModelAndView().getModel().get("users");
            assertThat(users).extracting(UserResponseDto::userId)
                    .as("문제 작성 미션은 출석이 아니다")
                    .containsExactly(active.getId());
        }

        @Test
        @DisplayName("같은 사용자가 두 번 로그인해도 한 번만 나온다")
        void deduplicatesRepeatedLogins() throws Exception {
            User user = fixtures.createUser();
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            MvcResult result = mockMvc.perform(get("/admin/analysis/daily-active-users")
                            .param("date", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<UserResponseDto> users = (List<UserResponseDto>) result.getModelAndView().getModel().get("users");
            assertThat(users).hasSize(1);
        }

        @Test
        @DisplayName("date 파라미터가 없으면 400으로 거절한다")
        void rejectsMissingDate() throws Exception {
            mockMvc.perform(get("/admin/analysis/daily-active-users"))
                    .andExpect(status().isBadRequest());
        }
    }
}
