package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.ApiResponse;
import com.aisip.OnO.blackbox.support.BlackboxStack;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공지(관리자가 웹에서 등록 → 앱이 조회하고 닫기)와 웹 피드백 설문.
 * 공지 등록에 필요한 관리자 폼 로그인까지만 본다. 관리자 화면 전체는 범위 밖이다.
 */
class NoticeAndFeedbackScenarioTest extends BlackboxTest {

    @Test
    void 관리자가_등록한_공지를_사용자가_보고_닫으면_그_사용자에게만_안_보인다() {
        String session = adminLogin();
        String title = "점검 안내 " + UUID.randomUUID().toString().substring(0, 8);
        ApiResponse registered = Api.anonymous().withHeader("Cookie", session).form("/admin/notice",
                Map.of("title", title, "content", "오늘 밤 점검합니다", "type", "WARNING", "durationHours", "2"));
        assertThat(registered.status()).isEqualTo(302);

        TestUser reader = TestUser.guest();
        TestUser another = TestUser.guest();
        JsonNode notice = reader.api().get("/api/notices/active").ok();
        assertThat(notice.path("title").asText()).isEqualTo(title);
        assertThat(notice.path("type").asText()).isEqualTo("WARNING");

        reader.api().post("/api/notices/" + notice.path("noticeId").asLong() + "/dismiss").ok();

        JsonNode afterDismiss = reader.api().get("/api/notices/active").ok();
        assertThat(afterDismiss.path("title").asText("")).isNotEqualTo(title);
        assertThat(another.api().get("/api/notices/active").ok().path("title").asText()).isEqualTo(title);
    }

    @Test
    void 일반_사용자_토큰으로는_관리자_화면에_들어갈_수_없다() {
        TestUser user = TestUser.guest();

        ApiResponse response = user.api().form("/admin/notice",
                Map.of("title", "침입", "content", "x", "type", "INFO"));

        assertThat(response.status()).isIn(302, 401, 403);
        assertThat(response.location()).doesNotEndWith("/admin/notice");
    }

    @Test
    void 틀린_비밀번호로는_관리자_로그인이_안_된다() {
        ApiResponse response = Api.anonymous().form("/perform_login",
                Map.of("username", BlackboxStack.ADMIN_IDENTIFIER, "password", "wrong-password"));

        assertThat(response.status()).isEqualTo(302);
        assertThat(response.location()).contains("/login?error");
    }

    @Test
    void 없는_공지를_닫으면_404() {
        TestUser user = TestUser.guest();

        user.api().post("/api/notices/999999999/dismiss").expectStatus(404);
    }

    @Test
    void 웹_피드백_설문을_제출하면_완료_페이지로_간다() {
        ApiResponse response = Api.anonymous().form("/feedback", Map.of(
                "usagePurpose", "내신",
                "usageFrequency", "매일"));

        assertThat(response.status()).isEqualTo(302);
        assertThat(response.location()).endsWith("/feedback/complete");
    }

    @Test
    void 공지_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/notices/active").expectStatus(401);
    }

    private static String adminLogin() {
        ApiResponse login = Api.anonymous().form("/perform_login",
                Map.of("username", BlackboxStack.ADMIN_IDENTIFIER, "password", BlackboxStack.ADMIN_PASSWORD));
        assertThat(login.status()).as("관리자 로그인 응답: %s", login.location()).isEqualTo(302);
        assertThat(login.location()).endsWith("/admin/main");
        String cookies = login.cookies();
        assertThat(cookies).contains("JSESSIONID");
        return cookies;
    }
}
