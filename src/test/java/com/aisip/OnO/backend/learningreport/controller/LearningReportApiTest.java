package com.aisip.OnO.backend.learningreport.controller;

import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("학습 리포트 API")
class LearningReportApiTest extends LearningReportTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 2, 21);

    private User owner;
    private User other;

    @BeforeEach
    void setUpUsers() {
        owner = fixtures.createUser("report-owner");
        other = fixtures.createOtherUser();
    }

    @Nested
    @DisplayName("리포트 조회")
    class GetReport {

        @Test
        @DisplayName("기준일을 주면 그 기준일로 계산한 리포트를 내려준다")
        void returnsReportForBaseDate() throws Exception {
            Problem problem = saveAnalyzedNote(owner.getId(), "대수", LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(owner.getId(), problem, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 600);
            saveSolve(owner.getId(), problem, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 300);

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports").param("baseDate", BASE_DATE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekly.periodLabel").value("WEEKLY"))
                    .andExpect(jsonPath("$.data.weekly.startDate").value("2026-02-15"))
                    .andExpect(jsonPath("$.data.weekly.endDate").value("2026-02-21"))
                    .andExpect(jsonPath("$.data.weekly.reviewCount").value(2))
                    .andExpect(jsonPath("$.data.weekly.noteWriteCount").value(1))
                    .andExpect(jsonPath("$.data.weekly.averageAccuracy").value(50.0))
                    .andExpect(jsonPath("$.data.weekly.averageStudyTimeMinutes").value(7.5))
                    .andExpect(jsonPath("$.data.weekly.consecutiveLearningDays").value(2))
                    .andExpect(jsonPath("$.data.weekly.trend.length()").value(7))
                    .andExpect(jsonPath("$.data.weekly.weakAreas[0].topic").value("대수"))
                    .andExpect(jsonPath("$.data.weekly.weakAreas[0].wrongCount").value(1))
                    .andExpect(jsonPath("$.data.monthly.periodLabel").value("MONTHLY"))
                    .andExpect(jsonPath("$.data.total.periodLabel").value("TOTAL"))
                    .andExpect(jsonPath("$.data.total.reviewCount").value(2))
                    .andExpect(jsonPath("$.data.weeklyComparison.basePeriod").value("WEEKLY"))
                    .andExpect(jsonPath("$.data.recommendations.actions.length()").value(3));
        }

        @Test
        @DisplayName("기준일을 생략하면 Asia/Seoul 기준 어제를 기준일로 쓴다")
        void defaultsToSeoulYesterday() throws Exception {
            LocalDate expected = LocalDate.now(KST).minusDays(1);

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekly.endDate").value(expected.toString()))
                    .andExpect(jsonPath("$.data.weekly.startDate").value(expected.minusDays(6).toString()));
        }

        @Test
        @DisplayName("기준일 형식이 잘못되면 400을 반환한다")
        void rejectsMalformedBaseDate() throws Exception {
            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports").param("baseDate", "2026/02/21"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("다른 사용자의 기록은 내 리포트에 들어가지 않는다")
        void doesNotLeakOtherUsersData() throws Exception {
            Problem theirs = saveAnalyzedNote(other.getId(), "확률", LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(other.getId(), theirs, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.WRONG, 600);

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports").param("baseDate", BASE_DATE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekly.reviewCount").value(0))
                    .andExpect(jsonPath("$.data.weekly.noteWriteCount").value(0))
                    .andExpect(jsonPath("$.data.total.reviewCount").value(0))
                    .andExpect(jsonPath("$.data.total.weakAreas.length()").value(0));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401을 반환한다")
        void rejectsUnauthenticated() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/learning-reports").param("baseDate", BASE_DATE.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("월간 요약 조회")
    class GetSummary {

        @Test
        @DisplayName("이번 달 복습 수와 목표 달성 현황을 내려준다")
        void returnsSummary() throws Exception {
            YearMonth thisMonth = YearMonth.from(LocalDate.now(KST));
            Problem problem = saveNoteWrittenAt(owner.getId(), thisMonth.atDay(1).atStartOfDay());
            for (int i = 0; i < 3; i++) {
                saveSolve(owner.getId(), problem,
                        thisMonth.atDay(1).atTime(1, 0).plusMinutes(i), AnswerStatus.CORRECT, 60);
            }

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.monthLabel")
                            .value(thisMonth.getYear() + "년 " + thisMonth.getMonthValue() + "월"))
                    .andExpect(jsonPath("$.data.monthlyReviewCount").value(3))
                    .andExpect(jsonPath("$.data.monthlyReviewGoal").value(30))
                    .andExpect(jsonPath("$.data.previousMonthlyReviewCount").value(0))
                    .andExpect(jsonPath("$.data.reviewCountDiff").value(3))
                    .andExpect(jsonPath("$.data.reviewCountChangeRate").value(100.0))
                    .andExpect(jsonPath("$.data.monthlyReviewGoalAchieved").value(false))
                    .andExpect(jsonPath("$.data.remainingReviewCountToGoal").value(27))
                    .andExpect(jsonPath("$.data.summaryMessage").value("이번 달 복습을 시작했어요"));
        }

        @Test
        @DisplayName("다른 사용자의 복습 수가 내 요약에 섞이지 않는다")
        void summaryIsScopedPerUser() throws Exception {
            YearMonth thisMonth = YearMonth.from(LocalDate.now(KST));
            Problem theirs = saveNoteWrittenAt(other.getId(), thisMonth.atDay(1).atStartOfDay());
            for (int i = 0; i < 7; i++) {
                saveSolve(other.getId(), theirs,
                        thisMonth.atDay(1).atTime(1, 0).plusMinutes(i), AnswerStatus.CORRECT, 60);
            }

            authenticateAs(owner.getId());

            mockMvc.perform(get("/api/learning-reports/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.monthlyReviewCount").value(0))
                    .andExpect(jsonPath("$.data.summaryMessage").value("이번 달 복습 기록을 만들어보세요"));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401을 반환한다")
        void rejectsUnauthenticated() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/learning-reports/summary"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
