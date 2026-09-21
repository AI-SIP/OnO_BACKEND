package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
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

    @BeforeEach
    void loginAsAdmin() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private Map<LocalDate, Long> dailyMap(MvcResult result, String name) {
        return (Map<LocalDate, Long>) result.getModelAndView().getModel().get(name);
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
                    .andExpect(model().attribute("allProblemCount", 0L))
                    .andExpect(model().attribute("allPracticeNoteCount", 0L))
                    .andExpect(model().attribute("allPracticeLogCount", 0L))
                    .andExpect(model().attribute("allProblemAnalysisCount", 0L))
                    .andExpect(model().attribute("periodVisitCount", 0L))
                    .andExpect(model().attribute("periodActiveUserCount", 0L))
                    .andExpect(model().attribute("periodUniqueVisitorCount", 0L))
                    .andExpect(model().attribute("periodPracticeNoteCount", 0L))
                    .andExpect(model().attribute("periodPracticeLogCount", 0L))
                    .andExpect(model().attribute("periodProblemCount", 0L))
                    .andExpect(model().attribute("periodAnalysisFailureRate", 0.0))
                    .andExpect(model().attribute("averageDailyVisitors", 0.0))
                    .andReturn();

            assertThat(dailyMap(result, "dailyActiveUsers").values())
                    .as("데이터가 없는 날짜도 비워두지 말고 0으로 채워야 그래프가 끊기지 않는다")
                    .containsOnly(0L);
        }

        @Test
        @DisplayName("기간을 지정하지 않으면 최근 30일을 본다")
        void defaultsToLastThirtyDays() throws Exception {
            mockMvc.perform(get("/admin/analysis"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("startDate", TODAY.minusDays(29)))
                    .andExpect(model().attribute("endDate", TODAY))
                    .andExpect(model().attribute("quickStart7Days", TODAY.minusDays(6)))
                    .andExpect(model().attribute("quickStart30Days", TODAY.minusDays(29)))
                    .andExpect(model().attribute("quickStart90Days", TODAY.minusDays(89)));
        }

        @Test
        @DisplayName("로그인 미션이 쌓이면 방문 수와 순 방문자 수로 집계된다")
        void aggregatesLoginMissionsAsVisits() throws Exception {
            User first = fixtures.createUser();
            User second = fixtures.createUser();
            saveMissionLog(first, MissionType.USER_LOGIN, null);
            saveMissionLog(second, MissionType.USER_LOGIN, null);

            MvcResult result = mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.toString())
                            .param("endDate", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("periodVisitCount", 2L))
                    .andExpect(model().attribute("periodUniqueVisitorCount", 2L))
                    .andExpect(model().attribute("averageDailyVisitors", 2.0))
                    .andReturn();

            assertThat(dailyMap(result, "dailyVisits").get(TODAY)).isEqualTo(2L);
            assertThat(dailyMap(result, "dailyActiveUsers").get(TODAY)).isEqualTo(2L);
        }

        @Test
        @DisplayName("같은 사용자가 여러 번 로그인해도 순 방문자는 1명으로 센다")
        void countsRepeatLoginAsSingleUniqueVisitor() throws Exception {
            User user = fixtures.createUser();
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.toString())
                            .param("endDate", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("periodVisitCount", 3L))
                    .andExpect(model().attribute("periodUniqueVisitorCount", 1L));
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
        @DisplayName("시작일과 종료일이 같은 하루 조회도 500 없이 처리한다")
        void handlesSingleDayRange() throws Exception {
            User user = fixtures.createUser();
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            MvcResult result = mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.toString())
                            .param("endDate", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(dailyMap(result, "dailyVisits"))
                    .as("하루짜리 조회는 정확히 하루치 항목만 있어야 한다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("기간 밖의 기록은 집계에서 빠진다")
        void excludesRecordsOutsideRange() throws Exception {
            User user = fixtures.createUser();
            var oldLog = saveMissionLog(user, MissionType.USER_LOGIN, null);
            forceCreatedAt("mission_log", oldLog.getId(), TODAY.minusDays(40).atTime(12, 0));

            mockMvc.perform(get("/admin/analysis")
                            .param("startDate", TODAY.minusDays(6).toString())
                            .param("endDate", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("periodVisitCount", 0L));
        }

        @Test
        @DisplayName("날짜 형식이 잘못되면 400으로 거절한다")
        void rejectsMalformedDate() throws Exception {
            mockMvc.perform(get("/admin/analysis").param("startDate", "2026-13-45"))
                    .andExpect(status().isBadRequest());
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
