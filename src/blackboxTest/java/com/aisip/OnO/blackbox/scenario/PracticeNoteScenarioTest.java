package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.Api;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import com.aisip.OnO.blackbox.support.ErrorCodes;
import com.aisip.OnO.blackbox.support.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복습노트 만들기, 문제 넣고 빼기, 알림, 복습 완료, 삭제와 다른 사용자 차단.
 */
class PracticeNoteScenarioTest extends BlackboxTest {

    @Test
    void 문제를_넣어_복습노트를_만들고_문제를_바꾸고_복습을_완료한다() {
        TestUser user = TestUser.guest();
        long p1 = Fixtures.registerProblem(user, "문제1");
        long p2 = Fixtures.registerProblem(user, "문제2");
        long p3 = Fixtures.registerProblem(user, "문제3");

        long note = Fixtures.createPracticeNote(user, "중간고사 대비", List.of(p1, p2));
        assertThat(problemIds(user.api().get("/api/practiceNotes/" + note).ok())).containsExactlyInAnyOrder(p1, p2);

        Map<String, Object> update = new HashMap<>();
        update.put("practiceNoteId", note);
        update.put("practiceTitle", "기말고사 대비");
        update.put("addProblemIdList", List.of(p3));
        update.put("removeProblemIdList", List.of(p1));
        user.api().patch("/api/practiceNotes", update).ok();

        user.api(Fixtures.CURRENT_APP_VERSION)
                .patch("/api/practiceNotes/" + note + "/complete", Map.of()).ok();

        JsonNode detail = user.api().get("/api/practiceNotes/" + note).ok();
        assertThat(detail.path("practiceTitle").asText()).isEqualTo("기말고사 대비");
        assertThat(problemIds(detail)).containsExactlyInAnyOrder(p2, p3);
        assertThat(detail.path("practiceCount").asLong()).isEqualTo(1);
        assertThat(detail.path("lastSolvedAt").isNull()).isFalse();

        JsonNode thumbnails = user.api().get("/api/practiceNotes/thumbnail/V2").ok().path("content");
        List<Long> ids = new ArrayList<>();
        thumbnails.forEach(t -> ids.add(t.path("practiceNoteId").asLong()));
        assertThat(ids).contains(note);
        assertThat(user.api().get("/api/practiceNotes/thumbnail").ok().isArray()).isTrue();
        assertThat(user.api().get("/api/practiceNotes/all").ok().isArray()).isTrue();
    }

    @Test
    void 매주_반복_알림을_요일_없이_만들면_거절한다() {
        TestUser user = TestUser.guest();
        Map<String, Object> body = new HashMap<>();
        body.put("practiceTitle", "알림 노트");
        body.put("problemIdList", List.of());
        body.put("practiceNotification", Map.of(
                "intervalDays", 1, "hour", 9, "minute", 0, "repeatType", "weekly", "weekDays", List.of()));

        user.api(Fixtures.CURRENT_APP_VERSION).post("/api/practiceNotes", body)
                .expectError(400, ErrorCodes.PRACTICE_NOTIFICATION_WEEK_DAYS_REQUIRED);
    }

    @Test
    void 요일을_넣은_매주_알림은_저장되고_조회된다() {
        TestUser user = TestUser.guest();
        Map<String, Object> body = new HashMap<>();
        body.put("practiceTitle", "알림 노트");
        body.put("problemIdList", List.of());
        body.put("practiceNotification", Map.of(
                "intervalDays", 1, "hour", 21, "minute", 30, "repeatType", "weekly", "weekDays", List.of(1, 3, 5)));

        long note = user.api(Fixtures.CURRENT_APP_VERSION).post("/api/practiceNotes", body).success().asLong();

        JsonNode notification = user.api().get("/api/practiceNotes/" + note).ok().path("practiceNotification");
        assertThat(notification.path("hour").asInt()).isEqualTo(21);
        assertThat(notification.path("weekDays")).hasSize(3);
    }

    @Test
    void 없는_감정_이모지로_복습을_완료하면_400() {
        TestUser user = TestUser.guest();
        long note = Fixtures.createPracticeNote(user, "이모지", List.of());

        user.api(Fixtures.CURRENT_APP_VERSION)
                .patch("/api/practiceNotes/" + note + "/complete", Map.of("moodEmojiKey", "no-such-emoji"))
                .expectError(400, ErrorCodes.INVALID_EMOJI_KEY);
    }

    @Test
    void 지운_복습노트는_404_이고_안의_문제는_남는다() {
        TestUser user = TestUser.guest();
        long problem = Fixtures.registerProblem(user, "남을 문제");
        long note = Fixtures.createPracticeNote(user, "지울 노트", List.of(problem));

        user.api().delete("/api/practiceNotes", Map.of("deletePracticeIdList", List.of(note))).ok();

        user.api().get("/api/practiceNotes/" + note).expectError(404, ErrorCodes.PRACTICE_NOTE_NOT_FOUND);
        user.api().get("/api/problems/" + problem).ok();
    }

    @Test
    void 전체_삭제하면_목록이_빈다() {
        TestUser user = TestUser.guest();
        Fixtures.createPracticeNote(user, "하나", List.of());

        user.api().delete("/api/practiceNotes/all").ok();

        assertThat(user.api().get("/api/practiceNotes/all").ok()).isEmpty();
    }

    @Test
    void 남의_복습노트는_조회_수정_완료_삭제할_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long note = Fixtures.createPracticeNote(owner, "주인 노트", List.of());

        other.api().get("/api/practiceNotes/" + note).expectDenied();
        other.api().patch("/api/practiceNotes", Map.of("practiceNoteId", note, "practiceTitle", "탈취")).expectDenied();
        other.api().patch("/api/practiceNotes/" + note + "/complete", Map.of()).expectDenied();
        other.api().delete("/api/practiceNotes", Map.of("deletePracticeIdList", List.of(note))).expectDenied();

        JsonNode detail = owner.api().get("/api/practiceNotes/" + note).ok();
        assertThat(detail.path("practiceTitle").asText()).isEqualTo("주인 노트");
        assertThat(detail.path("practiceCount").asLong()).isZero();
    }

    @Test
    void 남의_문제를_내_복습노트에_넣을_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        long ownersProblem = Fixtures.registerProblem(owner, "주인 문제");

        Map<String, Object> body = new HashMap<>();
        body.put("practiceTitle", "남의 문제 담기");
        body.put("problemIdList", List.of(ownersProblem));
        other.api().post("/api/practiceNotes", body).expectClientError();

        long note = Fixtures.createPracticeNote(other, "내 노트", List.of());
        other.api().patch("/api/practiceNotes", Map.of("practiceNoteId", note, "addProblemIdList", List.of(ownersProblem)))
                .expectClientError();
        assertThat(problemIds(other.api().get("/api/practiceNotes/" + note).ok())).isEmpty();
    }

    @Test
    void 잘못된_요청은_400() {
        TestUser user = TestUser.guest();

        user.api().patch("/api/practiceNotes", Map.of("practiceTitle", "id 없음")).expectStatus(400);
        user.api().delete("/api/practiceNotes", Map.of()).expectStatus(400);
        user.api().get("/api/practiceNotes/thumbnail/V2", Map.of("size", 101)).expectStatus(400);
    }

    @Test
    void 복습노트_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/practiceNotes/all").expectStatus(401);
        Api.anonymous().post("/api/practiceNotes", Map.of()).expectStatus(401);
    }

    private static List<Long> problemIds(JsonNode detail) {
        List<Long> ids = new ArrayList<>();
        detail.path("problemIdList").forEach(id -> ids.add(id.asLong()));
        return ids;
    }
}
