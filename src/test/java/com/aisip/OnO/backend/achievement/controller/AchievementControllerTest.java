package com.aisip.OnO.backend.achievement.controller;

import com.aisip.OnO.backend.achievement.support.AchievementTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트와 맞춘 응답 형태를 고정한다. 필드 이름이나 순서가 바뀌면 앱이 그대로 깨진다.
 */
@DisplayName("훈장 API 계약")
class AchievementControllerTest extends AchievementTestSupport {

    @Test
    @DisplayName("GET /api/achievements - 열두 개가 훈장표 순서로 내려간다")
    void returnsAllTwelveInOrder() throws Exception {
        User user = fixtures.createUser();

        mockMvc.perform(get("/api/achievements").with(asUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievements.length()").value(12))
                .andExpect(jsonPath("$.data.achievements[*].key").value(Matchers.contains(
                        "first_step", "archivist", "persistence", "phoenix", "dawn_class", "night_owl",
                        "perfect_month", "flawless", "organizer", "reviewer", "companion", "cheerleader")))
                .andExpect(jsonPath("$.data.achievements[0].nameKo").value("첫 걸음"))
                .andExpect(jsonPath("$.data.achievements[0].descriptionKo").value("오답노트를 처음 적었어요"))
                .andExpect(jsonPath("$.data.achievements[0].imageUrl").value("assets/Medal/first_step.png"))
                .andExpect(jsonPath("$.data.achievements[0].earned").value(false))
                .andExpect(jsonPath("$.data.achievements[0].earnedAt").doesNotExist())
                .andExpect(jsonPath("$.data.newlyEarned").isArray())
                .andExpect(jsonPath("$.data.newlyEarned").value(Matchers.empty()));
    }

    @Test
    @DisplayName("GET /api/achievements - 받은 훈장은 earnedAt 이 차고 newlyEarned 에 실린다")
    void earnedAchievementCarriesEarnedAt() throws Exception {
        User user = fixtures.createUser();
        Problem problem = saveProblem(user.getId());
        saveSolveSequence(problem, user.getId(), NOON, AnswerStatus.WRONG, AnswerStatus.CORRECT);

        String earned = "$.data.achievements[?(@.key == 'phoenix')]";
        mockMvc.perform(get("/api/achievements").with(asUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(earned + ".earned").value(Matchers.contains(true)))
                .andExpect(jsonPath(earned + ".earnedAt").value(Matchers.contains(Matchers.notNullValue())))
                .andExpect(jsonPath("$.data.newlyEarned")
                        .value(Matchers.containsInAnyOrder("first_step", "phoenix")));
    }

    @Test
    @DisplayName("GET /api/achievements - 진행도가 있는 훈장은 current/target 이 차고, 없는 훈장은 null 이다")
    void progressFields() throws Exception {
        User user = fixtures.createUser();
        saveFolders(user.getId(), 4);

        String organizer = "$.data.achievements[?(@.key == 'organizer')]";
        String phoenix = "$.data.achievements[?(@.key == 'phoenix')]";
        mockMvc.perform(get("/api/achievements").with(asUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(organizer + ".current").value(Matchers.contains(4)))
                .andExpect(jsonPath(organizer + ".target").value(Matchers.contains(10)))
                .andExpect(jsonPath(phoenix + ".current").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath(phoenix + ".target").value(Matchers.contains(Matchers.nullValue())));
    }

    @Test
    @DisplayName("GET /api/achievements - 남의 훈장은 안 보인다")
    void doesNotLeakOtherUsersAchievements() throws Exception {
        User owner = fixtures.createUser();
        User other = fixtures.createOtherUser();
        saveProblems(owner.getId(), 1);
        achievementService.getAchievements(owner.getId());

        mockMvc.perform(get("/api/achievements").with(asUser(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievements[?(@.earned == true)]").value(Matchers.empty()))
                .andExpect(jsonPath("$.data.newlyEarned").value(Matchers.empty()));
    }

    @Test
    @DisplayName("GET /api/achievements - 비로그인은 401")
    void requiresAuthentication() throws Exception {
        clearAuthentication();

        mockMvc.perform(get("/api/achievements"))
                .andExpect(status().isUnauthorized());
    }
}
