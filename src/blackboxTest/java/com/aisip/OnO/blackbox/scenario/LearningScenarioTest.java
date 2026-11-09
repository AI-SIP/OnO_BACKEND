package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.TestUser;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 학습 캘린더와 학습 리포트. 오답노트 등록과 복습 기록이 집계에 반영되는지 본다.
 */
class LearningScenarioTest extends BlackboxTest {

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 오늘_복습하면_캘린더의_오늘_칸에_기록되고_기분을_남길_수_있다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "캘린더 문제");
        ProblemSolveScenarioTest.registerSolve(user, problemId, "CORRECT");
        LocalDate today = today();

        JsonNode mood = user.api().patch("/api/learning-calendar/mood",
                Map.of("date", today.toString(), "emojiKey", "happy_tears")).ok();
        assertThat(mood.path("emojiKey").asText()).isEqualTo("happy_tears");

        JsonNode calendar = user.api().get("/api/learning-calendar",
                Map.of("year", today.getYear(), "month", today.getMonthValue())).ok();
        JsonNode todayRecord = null;
        for (JsonNode record : calendar.path("records")) {
            if (record.path("date").asText().equals(today.toString())) {
                todayRecord = record;
            }
        }
        assertThat(todayRecord).as("오늘 기록: %s", calendar).isNotNull();
        assertThat(todayRecord.path("hasStudied").asBoolean()).isTrue();
        assertThat(todayRecord.path("reviewCount").asInt()).isEqualTo(1);
        assertThat(todayRecord.path("moodEmojiKey").asText()).isEqualTo("happy_tears");
        assertThat(calendar.path("currentStreak").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 잘못된_달이나_이모지는_400() {
        TestUser user = TestUser.guest();

        user.api().get("/api/learning-calendar", Map.of("year", 2026, "month", 13)).expectClientError();
        user.api().patch("/api/learning-calendar/mood", Map.of("date", today().toString(), "emojiKey", "nope"))
                .expectError(400, ErrorCodes.INVALID_EMOJI_KEY);
    }

    @Test
    void 학습_리포트_요약과_개요와_전체_리포트가_복습_수를_반영한다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "리포트 문제");
        ProblemSolveScenarioTest.registerSolve(user, problemId, "WRONG");

        JsonNode summary = user.api().get("/api/learning-reports/summary").ok();
        assertThat(summary.path("monthlyReviewCount").asLong()).isEqualTo(1);

        user.api().get("/api/learning-reports/overview").ok();
        user.api().get("/api/learning-reports/overview", Map.of("period", "MONTH")).ok();

        JsonNode report = user.api().get("/api/learning-reports").ok();
        assertThat(report.has("weekly")).isTrue();
        assertThat(report.has("recommendations")).isTrue();
    }

    @Test
    void 잘못된_기간_값은_400() {
        TestUser user = TestUser.guest();

        user.api().get("/api/learning-reports/overview", Map.of("period", "DECADE")).expectStatus(400);
        user.api().get("/api/learning-reports", Map.of("baseDate", "not-a-date")).expectStatus(400);
    }

    @Test
    void 학습_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/learning-calendar", Map.of("year", 2026, "month", 1)).expectStatus(401);
        Api.anonymous().get("/api/learning-reports/summary").expectStatus(401);
    }
}
