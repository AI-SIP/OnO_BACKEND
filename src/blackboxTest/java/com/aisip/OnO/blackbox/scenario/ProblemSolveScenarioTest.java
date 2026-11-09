package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.Images;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복습 기록(풀이 기록) 등록, 이미지, 수정, 삭제와 문제 쪽 집계 반영, 다른 사용자 차단.
 */
class ProblemSolveScenarioTest extends BlackboxTest {

    @Test
    void 복습_기록을_남기면_문제의_풀이_횟수와_다음_복습일이_바뀐다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "복습할 문제");

        long solveId = registerSolve(user, problemId, "CORRECT");

        JsonNode solve = user.api().get("/api/problem-solves/" + solveId).ok();
        assertThat(solve.path("problemId").asLong()).isEqualTo(problemId);
        assertThat(solve.path("answerStatus").asText()).isEqualTo("CORRECT");
        assertThat(solve.path("improvements").get(0).asText()).isEqualTo("BETTER_UNDERSTANDING");

        assertThat(user.api().get("/api/problem-solves/problem/" + problemId + "/count").ok().asLong()).isEqualTo(1);
        assertThat(user.api().get("/api/problem-solves/user/count").ok().asLong()).isEqualTo(1);
        assertThat(user.api().get("/api/problem-solves/problem/" + problemId).ok()).hasSize(1);
        assertThat(user.api().get("/api/problem-solves/user").ok()).hasSize(1);

        JsonNode problem = user.api().get("/api/problems/" + problemId).ok();
        assertThat(problem.path("solveCount").asLong()).isEqualTo(1);
        assertThat(problem.path("nextReviewAt").isNull()).isFalse();
    }

    @Test
    void 복습_기록에_이미지를_붙이고_수정하고_지운다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "이미지 복습");
        long solveId = registerSolve(user, problemId, "WRONG");
        String image = Images.uploadViaPresignedUrl(user);

        user.api().post("/api/problem-solves/" + solveId + "/image-urls", Map.of("imageUrls", List.of(image))).success();
        Map<String, Object> update = new HashMap<>();
        update.put("problemSolveId", solveId);
        update.put("answerStatus", "PARTIAL");
        update.put("reflection", "절반은 맞았다");
        user.api().patch("/api/problem-solves", update).ok();

        JsonNode solve = user.api().get("/api/problem-solves/" + solveId).ok();
        assertThat(solve.path("imageUrls").get(0).asText()).isEqualTo(image);
        assertThat(solve.path("answerStatus").asText()).isEqualTo("PARTIAL");
        assertThat(solve.path("reflection").asText()).isEqualTo("절반은 맞았다");

        user.api().delete("/api/problem-solves/" + solveId).ok();
        user.api().get("/api/problem-solves/" + solveId).expectError(404, ErrorCodes.PROBLEM_SOLVE_NOT_FOUND);
        assertThat(user.api().get("/api/problem-solves/problem/" + problemId + "/count").ok().asLong()).isZero();
    }

    @Test
    void 멀티파트로_복습_이미지를_올린다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "멀티파트 복습");
        long solveId = registerSolve(user, problemId, "CORRECT");

        user.api().multipart("POST", "/api/problem-solves/" + solveId + "/images", Map.of(),
                "images", "solve.png", "image/png", Images.PNG).ok();

        assertThat(user.api().get("/api/problem-solves/" + solveId).ok().path("imageUrls")).hasSize(1);
    }

    @Test
    void 남의_문제에_복습_기록을_남길_수_없고_남의_기록은_보거나_고치거나_지울_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long problemId = Fixtures.registerProblem(owner, "주인 문제");
        long solveId = registerSolve(owner, problemId, "CORRECT");

        other.api(Fixtures.CURRENT_APP_VERSION).post("/api/problem-solves", solveBody(problemId, "WRONG")).expectDenied();
        other.api().get("/api/problem-solves/" + solveId).expectDenied();
        other.api().get("/api/problem-solves/problem/" + problemId).expectDenied();
        other.api().patch("/api/problem-solves", Map.of("problemSolveId", solveId, "answerStatus", "WRONG")).expectDenied();
        other.api().delete("/api/problem-solves/" + solveId).expectDenied();

        assertThat(owner.api().get("/api/problem-solves/" + solveId).ok().path("answerStatus").asText()).isEqualTo("CORRECT");
        assertThat(owner.api().get("/api/problem-solves/problem/" + problemId + "/count").ok().asLong()).isEqualTo(1);
    }

    @Test
    void 필수값이_없거나_감정_이모지가_틀리면_400() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "검증");

        Map<String, Object> noStatus = solveBody(problemId, "CORRECT");
        noStatus.remove("answerStatus");
        user.api().post("/api/problem-solves", noStatus).expectStatus(400);
        user.api().post("/api/problem-solves", Map.of("answerStatus", "CORRECT")).expectStatus(400);
        Map<String, Object> badEmoji = solveBody(problemId, "CORRECT");
        badEmoji.put("moodEmojiKey", "no-such-emoji");
        user.api().post("/api/problem-solves", badEmoji).expectError(400, ErrorCodes.INVALID_EMOJI_KEY);
        Map<String, Object> badEnum = solveBody(problemId, "MAYBE");
        user.api().post("/api/problem-solves", badEnum).expectStatus(400);
    }

    @Test
    void 복습_기록_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/problem-solves/user").expectStatus(401);
        Api.anonymous().post("/api/problem-solves", Map.of()).expectStatus(401);
    }

    static long registerSolve(TestUser user, long problemId, String answerStatus) {
        return user.api(Fixtures.CURRENT_APP_VERSION).post("/api/problem-solves", solveBody(problemId, answerStatus))
                .success().asLong();
    }

    static Map<String, Object> solveBody(long problemId, String answerStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("problemId", problemId);
        body.put("practicedAt", Fixtures.now().toString());
        body.put("answerStatus", answerStatus);
        body.put("reflection", "개념을 다시 봤다");
        body.put("improvements", List.of("BETTER_UNDERSTANDING"));
        body.put("timeSpentSeconds", 180);
        body.put("moodEmojiKey", "cool_sunglasses");
        return body;
    }
}
