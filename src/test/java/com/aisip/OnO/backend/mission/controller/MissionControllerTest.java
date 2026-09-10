package com.aisip.OnO.backend.mission.controller;

import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트와 맞춘 응답 형태를 고정한다. 필드 이름이나 묶음 구조가 바뀌면 앱이 그대로 깨진다.
 */
@DisplayName("미션 API 계약")
class MissionControllerTest extends MissionSystemTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
        authenticateAs(user.getId());
    }

    @Test
    @DisplayName("GET /api/missions - daily 와 weekly 를 각자의 periodKey 와 함께 내려준다")
    void getMissions() throws Exception {
        completeMission(user.getId(), DAILY_NOTE_WRITE);

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily.periodKey").value(periodKeyOf(DAILY_NOTE_WRITE)))
                .andExpect(jsonPath("$.data.weekly.periodKey").value(periodKeyOf(WEEKLY_NOTE_10)))
                .andExpect(jsonPath("$.data.daily.missions.length()").value(6))
                .andExpect(jsonPath("$.data.weekly.missions.length()").value(4))
                .andExpect(jsonPath("$.data.daily.missions[1].code").value(DAILY_NOTE_WRITE))
                .andExpect(jsonPath("$.data.daily.missions[1].title").value("오늘의 오답"))
                .andExpect(jsonPath("$.data.daily.missions[1].description").value("오답노트 1개 쓰기"))
                .andExpect(jsonPath("$.data.daily.missions[1].iconKey").value("note_write"))
                .andExpect(jsonPath("$.data.daily.missions[1].category").value("DAILY"))
                .andExpect(jsonPath("$.data.daily.missions[1].current").value(1))
                .andExpect(jsonPath("$.data.daily.missions[1].target").value(1))
                .andExpect(jsonPath("$.data.daily.missions[1].completed").value(true))
                .andExpect(jsonPath("$.data.daily.missions[1].claimed").value(false))
                .andExpect(jsonPath("$.data.daily.missions[1].rewardType").value("XP"))
                .andExpect(jsonPath("$.data.daily.missions[1].rewardValue").value(10))
                .andExpect(jsonPath("$.data.daily.missions[1].progressId").isNumber())
                .andExpect(jsonPath("$.data.daily.missions[1].periodKey").value(periodKeyOf(DAILY_NOTE_WRITE)))
                .andExpect(jsonPath("$.data.weekly.missions[0].periodKey").value(periodKeyOf(WEEKLY_ATTEND_5)))
                .andExpect(jsonPath("$.data.weekly.missions[0].progressId").doesNotExist())
                .andExpect(jsonPath("$.data.expired.periodKey").doesNotExist())
                .andExpect(jsonPath("$.data.expired.missions").isArray())
                .andExpect(jsonPath("$.data.expired.missions.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/missions - 기간이 지난 미수령 보상이 expired 로 내려온다")
    void getMissionsIncludesExpired() throws Exception {
        Long progressId = insertCompletedProgress(
                user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired.missions.length()").value(1))
                .andExpect(jsonPath("$.data.expired.missions[0].progressId").value(progressId))
                .andExpect(jsonPath("$.data.expired.missions[0].code").value(WEEKLY_REVIEW_30))
                .andExpect(jsonPath("$.data.expired.missions[0].periodKey").value(lastWeekKey()))
                .andExpect(jsonPath("$.data.expired.missions[0].completed").value(true))
                .andExpect(jsonPath("$.data.expired.missions[0].claimed").value(false));
    }

    @Test
    @DisplayName("POST /api/missions/{progressId}/claim - 기간이 지난 미수령 보상도 받을 수 있다")
    void claimExpired() throws Exception {
        Long progressId = insertCompletedProgress(
                user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/missions/{progressId}/claim", progressId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardValue").value(100));
    }

    @Test
    @DisplayName("GET /api/missions - 인증이 없으면 401")
    void getMissionsRequiresAuthentication() throws Exception {
        clearAuthentication();

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/missions/{progressId}/claim - 보상 결과를 내려준다")
    void claim() throws Exception {
        MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);

        mockMvc.perform(post("/api/missions/{progressId}/claim", progress.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressId").value(progress.getId()))
                .andExpect(jsonPath("$.data.rewardType").value("XP"))
                .andExpect(jsonPath("$.data.rewardValue").value(10))
                .andExpect(jsonPath("$.data.totalStudyLevel").isNumber())
                .andExpect(jsonPath("$.data.leveledUp").isBoolean());
    }

    @Test
    @DisplayName("GET /api/missions/history - 커서 페이지 규약을 그대로 따른다")
    void getClaimHistory() throws Exception {
        Long progressId = insertClaimedProgress(
                user.getId(), WEEKLY_NOTE_10, "2020-W36", LocalDateTime.of(2020, 9, 1, 10, 0));

        mockMvc.perform(get("/api/missions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalClaimedXp").value(80))
                .andExpect(jsonPath("$.data.totalClaimedCount").value(1))
                .andExpect(jsonPath("$.data.content[0].progressId").value(progressId))
                .andExpect(jsonPath("$.data.content[0].code").value(WEEKLY_NOTE_10))
                .andExpect(jsonPath("$.data.content[0].title").value("열 권의 노트"))
                .andExpect(jsonPath("$.data.content[0].iconKey").value("note_write"))
                .andExpect(jsonPath("$.data.content[0].category").value("WEEKLY"))
                .andExpect(jsonPath("$.data.content[0].periodKey").value("2020-W36"))
                .andExpect(jsonPath("$.data.content[0].rewardType").value("XP"))
                .andExpect(jsonPath("$.data.content[0].rewardValue").value(80))
                .andExpect(jsonPath("$.data.content[0].claimedAt").value("2020-09-01T10:00:00"));
    }

    @Test
    @DisplayName("GET /api/missions/history - 두 번째 페이지에는 합계가 없다")
    void getClaimHistorySecondPage() throws Exception {
        Long first = insertClaimedProgress(
                user.getId(), WEEKLY_NOTE_10, "2020-W36", LocalDateTime.of(2020, 9, 2, 10, 0));
        insertClaimedProgress(
                user.getId(), DAILY_MOOD, "2020-09-01", LocalDateTime.of(2020, 9, 1, 10, 0));

        mockMvc.perform(get("/api/missions/history").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(first))
                .andExpect(jsonPath("$.data.totalClaimedCount").value(2));

        mockMvc.perform(get("/api/missions/history")
                        .param("size", "1")
                        .param("cursor", String.valueOf(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value(DAILY_MOOD))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalClaimedXp").doesNotExist())
                .andExpect(jsonPath("$.data.totalClaimedCount").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/missions/history - 인증이 없으면 401")
    void getClaimHistoryRequiresAuthentication() throws Exception {
        clearAuthentication();

        mockMvc.perform(get("/api/missions/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/missions/{progressId}/claim - 완료하지 않았으면 7011")
    void claimRejectsIncomplete() throws Exception {
        missionProgressUpdater.increase(user.getId(),
                definitionOf(DAILY_REVIEW_3).getMetric(), 1);
        MissionProgress progress = progressOf(user, DAILY_REVIEW_3);

        mockMvc.perform(post("/api/missions/{progressId}/claim", progress.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(7011));
    }

    @Test
    @DisplayName("POST /api/missions/{progressId}/claim - 이미 받았으면 7012")
    void claimRejectsDuplicate() throws Exception {
        MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
        missionService.claim(user.getId(), progress.getId());

        mockMvc.perform(post("/api/missions/{progressId}/claim", progress.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(7012));
    }

    @Test
    @DisplayName("POST /api/missions/{progressId}/claim - 남의 미션이면 7010")
    void claimRejectsOtherUser() throws Exception {
        User other = fixtures.createOtherUser();
        MissionProgress progress = completeMission(other.getId(), DAILY_NOTE_WRITE);

        mockMvc.perform(post("/api/missions/{progressId}/claim", progress.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(7010));
    }
}
