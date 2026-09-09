package com.aisip.OnO.backend.mission.controller;

import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                .andExpect(jsonPath("$.data.daily.missions[1].description").value("오답노트 1개 등록"))
                .andExpect(jsonPath("$.data.daily.missions[1].iconKey").value("note_write"))
                .andExpect(jsonPath("$.data.daily.missions[1].category").value("DAILY"))
                .andExpect(jsonPath("$.data.daily.missions[1].current").value(1))
                .andExpect(jsonPath("$.data.daily.missions[1].target").value(1))
                .andExpect(jsonPath("$.data.daily.missions[1].completed").value(true))
                .andExpect(jsonPath("$.data.daily.missions[1].claimed").value(false))
                .andExpect(jsonPath("$.data.daily.missions[1].rewardType").value("XP"))
                .andExpect(jsonPath("$.data.daily.missions[1].rewardValue").value(10))
                .andExpect(jsonPath("$.data.daily.missions[1].progressId").isNumber());
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
