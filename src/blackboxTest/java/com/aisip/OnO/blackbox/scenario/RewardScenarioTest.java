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
 * 미션, 업적, 꾸미기. 학습 활동이 미션 진행으로 이어지고 보상을 받아 아이템을 장착하는 흐름이다.
 */
class RewardScenarioTest extends BlackboxTest {

    @Test
    void 활동으로_완료한_미션의_보상을_한_번만_받는다() {
        TestUser user = TestUser.guest();
        doDailyActivities(user);

        JsonNode missions = user.api(Fixtures.CURRENT_APP_VERSION).get("/api/missions").ok();
        JsonNode completed = firstMatching(missions, true, false);
        assertThat(completed).as("완료되고 아직 안 받은 미션이 있어야 합니다: %s", missions).isNotNull();
        long progressId = completed.path("progressId").asLong();

        JsonNode claimed = user.api(Fixtures.CURRENT_APP_VERSION).post("/api/missions/" + progressId + "/claim").ok();
        assertThat(claimed.path("progressId").asLong()).isEqualTo(progressId);
        assertThat(claimed.path("rewardValue").asInt()).isPositive();

        user.api(Fixtures.CURRENT_APP_VERSION).post("/api/missions/" + progressId + "/claim")
                .expectError(400, ErrorCodes.MISSION_ALREADY_CLAIMED);

        JsonNode history = user.api().get("/api/missions/history").ok();
        assertThat(history.toString()).contains(String.valueOf(progressId));
    }

    @Test
    void 출석하면_출석_미션이_완료된다() {
        TestUser user = TestUser.guest();

        user.api(Fixtures.CURRENT_APP_VERSION).get("/api/users").ok();

        JsonNode attend = mission(user.api(Fixtures.CURRENT_APP_VERSION).get("/api/missions").ok(), "DAILY_ATTEND");
        assertThat(attend.path("completed").asBoolean()).as("출석 미션: %s", attend).isTrue();
    }

    @Test
    void 완료하지_않은_미션은_받을_수_없다() {
        TestUser user = TestUser.guest();
        // 복습 1번이면 "3문제 복습" 미션이 1/3 으로 진행 중이 된다.
        long problemId = Fixtures.registerProblem(user, "진행 중 미션");
        ProblemSolveScenarioTest.registerSolve(user, problemId, "CORRECT");

        JsonNode missions = user.api(Fixtures.CURRENT_APP_VERSION).get("/api/missions").ok();
        JsonNode incomplete = firstMatching(missions, false, false);
        assertThat(incomplete).as("아직 완료되지 않은 미션: %s", missions).isNotNull();

        user.api(Fixtures.CURRENT_APP_VERSION).post("/api/missions/" + incomplete.path("progressId").asLong() + "/claim")
                .expectError(400, ErrorCodes.MISSION_NOT_COMPLETED);
    }

    @Test
    void 남의_미션_보상을_대신_받을_수_없다() {
        TestUser owner = TestUser.guest();
        TestUser other = TestUser.guest();
        doDailyActivities(owner);
        JsonNode completed = firstMatching(owner.api(Fixtures.CURRENT_APP_VERSION).get("/api/missions").ok(), true, false);
        assertThat(completed).isNotNull();
        long progressId = completed.path("progressId").asLong();

        other.api(Fixtures.CURRENT_APP_VERSION).post("/api/missions/" + progressId + "/claim").expectDenied();

        owner.api(Fixtures.CURRENT_APP_VERSION).post("/api/missions/" + progressId + "/claim").ok();
    }

    @Test
    void 업적_목록을_조회한다() {
        TestUser user = TestUser.guest();
        doDailyActivities(user);

        JsonNode achievements = user.api().get("/api/achievements").ok();

        assertThat(achievements.path("achievements").isArray()).isTrue();
        assertThat(achievements.path("achievements")).isNotEmpty();
    }

    @Test
    void 가진_아이템은_장착되고_없는_아이템은_거절된다() {
        TestUser user = TestUser.guest();
        JsonNode cosmetics = user.api().get("/api/cosmetics").ok();
        JsonNode owned = null;
        JsonNode notOwned = null;
        for (JsonNode item : cosmetics.path("items")) {
            if (owned == null && item.path("owned").asBoolean() && !"BASE".equals(item.path("slot").asText())) {
                owned = item;
            }
            if (notOwned == null && !item.path("owned").asBoolean()) {
                notOwned = item;
            }
        }
        assertThat(notOwned).as("새 사용자에게 잠긴 아이템이 있어야 합니다").isNotNull();

        user.api().put("/api/cosmetics/equip",
                        Map.of("slot", notOwned.path("slot").asText(), "itemKey", notOwned.path("itemKey").asText()))
                .expectError(400, ErrorCodes.COSMETIC_ITEM_NOT_OWNED);
        user.api().put("/api/cosmetics/equip", Map.of("slot", "OUTFIT", "itemKey", "no_such_item"))
                .expectError(404, ErrorCodes.COSMETIC_ITEM_NOT_FOUND);

        if (owned != null) {
            String slot = owned.path("slot").asText();
            String key = owned.path("itemKey").asText();
            user.api().put("/api/cosmetics/equip", Map.of("slot", slot, "itemKey", key)).ok();
            assertThat(user.api().get("/api/cosmetics").ok().path("equipped").path(slot).asText()).isEqualTo(key);

            Map<String, Object> unequip = new HashMap<>();
            unequip.put("slot", slot);
            unequip.put("itemKey", null);
            user.api().put("/api/cosmetics/equip", unequip).ok();
        }
    }

    @Test
    void 꾸미기_요청_검증() {
        TestUser user = TestUser.guest();

        user.api().put("/api/cosmetics/equip", Map.of("itemKey", "x")).expectStatus(400);
        user.api().put("/api/cosmetics/equip-all", Map.of()).expectStatus(400);
        user.api().put("/api/cosmetics/equip-set", Map.of("setId", "")).expectStatus(400);
        user.api().put("/api/cosmetics/equip-set", Map.of("setId", "no_such_set")).expectClientError();
        user.api().put("/api/cosmetics/equip-all", Map.of("equipped", Map.of())).ok();
    }

    @Test
    void 보상_API_는_토큰이_없으면_401() {
        Api.anonymous().get("/api/missions").expectStatus(401);
        Api.anonymous().get("/api/achievements").expectStatus(401);
        Api.anonymous().get("/api/cosmetics").expectStatus(401);
    }

    /** 앱을 켜고(로그인), 오답노트를 쓰고, 복습하는 하루치 활동. 현재 앱 버전으로 보낸다. */
    private static void doDailyActivities(TestUser user) {
        user.api(Fixtures.CURRENT_APP_VERSION).get("/api/users").ok();
        long problemId = Fixtures.registerProblem(user, "미션용 문제");
        ProblemSolveScenarioTest.registerSolve(user, problemId, "CORRECT");
    }

    private static JsonNode mission(JsonNode missions, String code) {
        for (String section : List.of("daily", "weekly")) {
            for (JsonNode m : missions.path(section).path("missions")) {
                if (code.equals(m.path("code").asText())) {
                    return m;
                }
            }
        }
        throw new AssertionError(code + " 미션이 없습니다: " + missions);
    }

    private static JsonNode firstMatching(JsonNode missions, boolean completed, boolean claimed) {
        List<JsonNode> all = new ArrayList<>();
        for (String section : List.of("daily", "weekly")) {
            missions.path(section).path("missions").forEach(all::add);
        }
        return all.stream()
                .filter(m -> m.path("completed").asBoolean() == completed && m.path("claimed").asBoolean() == claimed)
                .filter(m -> !m.path("progressId").isNull() && m.path("progressId").asLong() > 0)
                .findFirst()
                .orElse(null);
    }
}
