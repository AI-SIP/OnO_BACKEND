package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.Images;
import com.aisip.OnO.blackbox.support.TestUser;
import com.aisip.OnO.blackbox.support.WireMock;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 분석. 요청 → RabbitMQ → 컨슈머가 S3 에서 이미지를 읽어 OpenAI(WireMock)에 보냄 → 결과 저장 → 앱이 조회.
 * 이 경로 전체가 실제로 이어지는지 본다. MockMvc 테스트는 OpenAIClient 와 프로듀서를 목으로 바꿔서 이 연결을 못 본다.
 */
class ProblemAnalysisScenarioTest extends BlackboxTest {

    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void 이미지가_있는_문제를_분석_요청하면_OpenAI_를_거쳐_결과가_저장된다() {
        TestUser user = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(user);
        long problemId = Fixtures.registerProblem(user, "분석할 문제", Fixtures.rootFolderId(user), List.of(image), List.of());
        int openAiCallsBefore = WireMock.count("POST", "/v1/chat/completions");

        user.api().post("/api/problems/" + problemId + "/analysis").ok();

        JsonNode analysis = eventually(ANALYSIS_TIMEOUT, () -> {
            JsonNode a = user.api().get("/api/problems/" + problemId + "/analysis").ok();
            assertThat(a.path("status").asText()).isEqualTo("COMPLETED");
            return a;
        });
        assertThat(analysis.path("subject").asText()).isEqualTo("수학");
        assertThat(keyPoints(analysis)).contains("근의 공식");
        assertThat(analysis.path("solution").asText()).doesNotContain("7");
        assertThat(user.api().get("/api/problems/" + problemId).ok().path("analysis").path("status").asText())
                .isEqualTo("COMPLETED");

        // 앱이 S3 에서 실제로 이미지를 읽어 data URL 로 실어 보냈는지 본다.
        assertThat(WireMock.count("POST", "/v1/chat/completions")).isGreaterThan(openAiCallsBefore);
        assertThat(WireMock.countContaining("POST", "/v1/chat/completions", "data:image/png;base64,")).isPositive();
    }

    @Test
    void 이미지가_없는_문제는_분석하지_않고_NO_IMAGE_가_된다() {
        TestUser user = TestUser.guest();
        long problemId = Fixtures.registerProblem(user, "이미지 없는 문제");

        user.api().post("/api/problems/" + problemId + "/analysis").ok();

        assertThat(user.api().get("/api/problems/" + problemId + "/analysis").ok().path("status").asText())
                .isEqualTo("NO_IMAGE");
    }

    @Test
    void OpenAI_가_실패하면_FAILED_가_되고_다시_요청해_성공하면_실패_문구가_지워진다() {
        allowServerErrorAlerts();
        TestUser user = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(user);
        long problemId = Fixtures.registerProblem(user, "실패할 문제", Fixtures.rootFolderId(user), List.of(image), List.of());
        // 이 사용자의 요청만 실패시키려고 문제 메모가 아니라 우선순위 높은 매핑을 잠깐 건다.
        String failing = WireMock.stub("POST", "/v1/chat/completions", 400,
                Map.of("error", Map.of("message", "blackbox forced failure", "type", "invalid_request_error")), 1);
        try {
            user.api().post("/api/problems/" + problemId + "/analysis").ok();
            eventually(ANALYSIS_TIMEOUT, () -> assertThat(
                    user.api().get("/api/problems/" + problemId + "/analysis").ok().path("status").asText())
                    .isEqualTo("FAILED"));
        } finally {
            WireMock.remove(failing);
        }

        user.api().post("/api/problems/" + problemId + "/analysis").ok();

        JsonNode analysis = eventually(ANALYSIS_TIMEOUT, () -> {
            JsonNode a = user.api().get("/api/problems/" + problemId + "/analysis").ok();
            assertThat(a.path("status").asText()).isEqualTo("COMPLETED");
            return a;
        });
        assertThat(analysis.path("errorMessage").isNull() || analysis.path("errorMessage").isMissingNode()).isTrue();
    }

    @Test
    void 하루_분석_한도_20회를_넘기면_RATE_LIMIT_EXCEEDED_가_된다() {
        TestUser user = TestUser.guest();
        String image = Images.uploadViaPresignedUrl(user);
        long root = Fixtures.rootFolderId(user);
        List<Map<String, Object>> problems = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            problems.add(Fixtures.problemBody("한도 " + i, root, List.of(image), List.of()));
        }
        JsonNode ids = user.api(Fixtures.CURRENT_APP_VERSION)
                .post("/api/problems/v2/batch", Map.of("problems", problems)).success();

        for (int i = 0; i < 20; i++) {
            user.api().post("/api/problems/" + ids.get(i).asLong() + "/analysis").ok();
        }
        long over = ids.get(20).asLong();
        user.api().post("/api/problems/" + over + "/analysis").ok();

        assertThat(user.api().get("/api/problems/" + over + "/analysis").ok().path("status").asText())
                .isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    private static List<String> keyPoints(JsonNode analysis) {
        List<String> points = new ArrayList<>();
        analysis.path("keyPoints").forEach(p -> points.add(p.asText()));
        return points;
    }
}
