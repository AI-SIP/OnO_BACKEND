package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementEarned;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.CosmeticOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.LegacyLogCount;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionDaily;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.SlotGroup;
import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.service.MissionPeriodKey;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("AdminGrowthController")
class AdminGrowthControllerTest extends AdminTestSupport {

    private User user;

    @BeforeEach
    void setUp() {
        authenticateAs(createAdminUser().getId(), "ROLE_ADMIN");
        user = fixtures.createUser();
    }

    private Map<String, Object> modelOf(MvcResult result) {
        return result.getModelAndView().getModel();
    }

    @Nested
    @DisplayName("치장")
    class Cosmetics {

        private void saveItem(String key, String slot, String name, Integer level, String ability, String setId, boolean active) {
            jdbcTemplate.update("""
                    INSERT INTO cosmetic_item (item_key, slot, name_ko, image_url, required_level, required_ability,
                                               full_body, set_id, set_name_ko, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())
                    """, key, slot, name, "assets/Cosmetic/" + key + ".png", level, ability,
                    setId, setId == null ? null : setId + " 세트", active);
        }

        private void equip(User owner, String slot, String key) {
            jdbcTemplate.update("INSERT INTO user_cosmetic_loadout (user_id, slot, item_key, updated_at) VALUES (?, ?, ?, NOW())",
                    owner.getId(), slot, key);
        }

        @Test
        @DisplayName("아이템이 없어도 빈 화면을 그린다")
        void rendersWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/cosmetics"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-cosmetics"))
                    .andReturn();

            CosmeticOverview overview = (CosmeticOverview) modelOf(result).get("overview");
            assertThat(overview.totalItems()).isZero();
            assertThat(overview.topItemName()).isNull();
            assertThat((List<?>) modelOf(result).get("groups")).isEmpty();
        }

        @Test
        @DisplayName("아이템마다 착용 유저 수를 세고, 자리를 그리는 층 순서로 묶는다")
        void groupsItemsBySlotWithEquipCount() throws Exception {
            saveItem("hat_beanie", "HEAD", "비니", 4, "PROBLEM_PRACTICE", "winter", true);
            saveItem("scarf", "NECK", "목도리", 2, "NOTE_PRACTICE", "winter", true);
            saveItem("bg_old", "BACKGROUND", "옛 배경", null, null, null, false);
            User other = fixtures.createOtherUser();
            equip(user, "HEAD", "hat_beanie");
            equip(other, "HEAD", "hat_beanie");
            equip(other, "NECK", "scarf");

            MvcResult result = mockMvc.perform(get("/admin/cosmetics")).andExpect(status().isOk()).andReturn();

            CosmeticOverview overview = (CosmeticOverview) modelOf(result).get("overview");
            assertThat(overview.totalItems()).isEqualTo(3);
            assertThat(overview.activeItems()).isEqualTo(2);
            assertThat(overview.usersWithLoadout()).isEqualTo(2);
            assertThat(overview.topItemName()).isEqualTo("비니");
            assertThat(overview.topItemEquipped()).isEqualTo(2);

            @SuppressWarnings("unchecked")
            List<SlotGroup> groups = (List<SlotGroup>) modelOf(result).get("groups");
            assertThat(groups).extracting(SlotGroup::slot).containsExactly("BACKGROUND", "NECK", "HEAD");
            assertThat(groups.get(2).items()).singleElement().satisfies(item -> {
                assertThat(item.equippedUsers()).isEqualTo(2);
                assertThat(item.unlockLabel()).isEqualTo("문제 복습 Lv.4");
                assertThat(item.hasRemoteImage()).as("번들 경로는 서버에서 못 띄운다").isFalse();
            });
            assertThat(groups.get(0).items().get(0).unlockLabel()).isEqualTo("기본 지급");
        }

        @Test
        @DisplayName("탈퇴한 유저의 착용 기록은 세지 않는다")
        void excludesDeletedUsers() throws Exception {
            saveItem("hat_beanie", "HEAD", "비니", 4, null, null, true);
            equip(user, "HEAD", "hat_beanie");
            jdbcTemplate.update("UPDATE user SET deleted_at = NOW() WHERE id = ?", user.getId());

            CosmeticOverview overview = (CosmeticOverview) modelOf(
                    mockMvc.perform(get("/admin/cosmetics")).andReturn()).get("overview");

            assertThat(overview.usersWithLoadout()).isZero();
            assertThat(overview.topItemName()).isNull();
        }

        @Test
        @DisplayName("slot 으로 한 자리만 보고, 모르는 slot 이면 전체를 보여준다")
        void filtersBySlot() throws Exception {
            saveItem("hat_beanie", "HEAD", "비니", 4, null, null, true);
            saveItem("scarf", "NECK", "목도리", 2, null, null, true);

            MvcResult head = mockMvc.perform(get("/admin/cosmetics").param("slot", "HEAD")).andReturn();
            assertThat((List<?>) modelOf(head).get("visibleGroups")).hasSize(1);
            assertThat(modelOf(head).get("selectedSlot")).isEqualTo("HEAD");

            MvcResult unknown = mockMvc.perform(get("/admin/cosmetics").param("slot", "NOPE"))
                    .andExpect(status().isOk()).andReturn();
            assertThat((List<?>) modelOf(unknown).get("visibleGroups")).hasSize(2);
            assertThat(modelOf(unknown).get("selectedSlot")).isNull();
        }
    }

    @Nested
    @DisplayName("업적")
    class Achievements {

        private void earn(User owner, Achievement achievement, LocalDateTime at) {
            jdbcTemplate.update("INSERT INTO user_achievement (user_id, achievement_key, earned_at) VALUES (?, ?, ?)",
                    owner.getId(), achievement.getKey(), at);
        }

        @Test
        @DisplayName("열두 개 훈장을 모두 보여주고, 받은 사람이 없으면 0 이다")
        void listsEveryAchievement() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/achievements"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-achievements"))
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<AchievementRow> rows = (List<AchievementRow>) modelOf(result).get("achievements");
            assertThat(rows).hasSize(Achievement.values().length);
            assertThat(rows).allSatisfy(r -> assertThat(r.earnedUsers()).isZero());
        }

        @Test
        @DisplayName("훈장별 획득 수와 비율, 최근 획득 목록을 집계한다")
        void aggregatesEarnedAchievements() throws Exception {
            User other = fixtures.createOtherUser();
            earn(user, Achievement.FIRST_STEP, LocalDateTime.now().minusDays(1));
            earn(other, Achievement.FIRST_STEP, LocalDateTime.now().minusDays(30));
            earn(user, Achievement.PHOENIX, LocalDateTime.now());

            MvcResult result = mockMvc.perform(get("/admin/achievements")).andReturn();

            AchievementOverview overview = (AchievementOverview) modelOf(result).get("overview");
            assertThat(overview.totalEarned()).isEqualTo(3);
            assertThat(overview.usersWithAny()).isEqualTo(2);
            assertThat(overview.earnedLast7Days()).isEqualTo(2);
            assertThat(overview.totalUsers()).as("관리자 계정은 분모에서 뺀다").isEqualTo(2);

            @SuppressWarnings("unchecked")
            List<AchievementRow> rows = (List<AchievementRow>) modelOf(result).get("achievements");
            AchievementRow firstStep = rows.stream().filter(r -> r.key().equals("first_step")).findFirst().orElseThrow();
            assertThat(firstStep.earnedUsers()).isEqualTo(2);
            assertThat(firstStep.rate()).isEqualTo(100.0);

            @SuppressWarnings("unchecked")
            List<AchievementEarned> recent = (List<AchievementEarned>) modelOf(result).get("recent");
            assertThat(recent).extracting(AchievementEarned::achievementName)
                    .containsExactly("불사조", "첫 걸음", "첫 걸음");
        }

        @Test
        @DisplayName("지금 enum 에 없는 키가 저장돼 있어도 화면이 열린다")
        void toleratesUnknownKey() throws Exception {
            jdbcTemplate.update("INSERT INTO user_achievement (user_id, achievement_key, earned_at) VALUES (?, 'removed_medal', NOW())",
                    user.getId());

            MvcResult result = mockMvc.perform(get("/admin/achievements")).andExpect(status().isOk()).andReturn();

            @SuppressWarnings("unchecked")
            List<AchievementEarned> recent = (List<AchievementEarned>) modelOf(result).get("recent");
            assertThat(recent).extracting(AchievementEarned::achievementName).containsExactly("removed_medal");
        }
    }

    @Nested
    @DisplayName("미션")
    class Missions {

        private Long saveMission(String code, String category, String metric, int target, boolean active) {
            jdbcTemplate.update("""
                    INSERT INTO mission_definition (code, title, description, icon_key, category, metric, target,
                                                    reward_type, reward_value, sort_order, active, created_at, updated_at)
                    VALUES (?, ?, '설명', 'icon', ?, ?, ?, 'XP', 10, 1, ?, NOW(), NOW())
                    """, code, code + " 제목", category, metric, target, active);
            return jdbcTemplate.queryForObject("SELECT id FROM mission_definition WHERE code = ?", Long.class, code);
        }

        private void progress(User owner, Long missionId, String periodKey, LocalDateTime completedAt, LocalDateTime claimedAt) {
            jdbcTemplate.update("""
                    INSERT INTO mission_progress (user_id, mission_id, period_key, current_value, target_snapshot,
                                                  completed_at, claimed_at, created_at, updated_at)
                    VALUES (?, ?, ?, 1, 1, ?, ?, NOW(), NOW())
                    """, owner.getId(), missionId, periodKey, completedAt, claimedAt);
        }

        @Test
        @DisplayName("미션이 없어도 30일 추이를 0 으로 채워 그린다")
        void rendersWhenEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/admin/missions"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-missions"))
                    .andReturn();

            @SuppressWarnings("unchecked")
            List<MissionDaily> trend = (List<MissionDaily>) modelOf(result).get("trend");
            assertThat(trend).hasSize(30);
            assertThat(trend.get(0).date()).isEqualTo(MissionPeriodKey.today());
            assertThat((List<?>) modelOf(result).get("dailyMissions")).isEmpty();
        }

        @Test
        @DisplayName("이번 기간과 누적 진행을 나눠 세고, 일일과 주간을 가른다")
        void aggregatesProgressByPeriod() throws Exception {
            LocalDate today = MissionPeriodKey.today();
            String todayKey = MissionPeriodKey.daily(today);
            String yesterdayKey = MissionPeriodKey.daily(today.minusDays(1));
            Long daily = saveMission("DAILY_ATTEND", "DAILY", "LOGIN_DAY", 1, true);
            Long weekly = saveMission("WEEKLY_NOTE_10", "WEEKLY", "PROBLEM_CREATED", 10, false);
            User other = fixtures.createOtherUser();
            LocalDateTime now = LocalDateTime.now();

            progress(user, daily, todayKey, now, now);
            progress(other, daily, todayKey, null, null);
            progress(user, daily, yesterdayKey, now.minusDays(1), null);
            progress(user, weekly, MissionPeriodKey.weekly(today), null, null);

            MvcResult result = mockMvc.perform(get("/admin/missions")).andReturn();

            @SuppressWarnings("unchecked")
            List<MissionRow> dailyMissions = (List<MissionRow>) modelOf(result).get("dailyMissions");
            assertThat(dailyMissions).singleElement().satisfies(m -> {
                assertThat(m.metricLabel()).isEqualTo("출석");
                assertThat(m.rewardLabel()).isEqualTo("10 XP");
                assertThat(m.currentParticipants()).isEqualTo(2);
                assertThat(m.currentCompleted()).isEqualTo(1);
                assertThat(m.currentClaimed()).isEqualTo(1);
                assertThat(m.currentCompletionRate()).isEqualTo(50.0);
                assertThat(m.totalCompleted()).isEqualTo(2);
                assertThat(m.totalClaimed()).isEqualTo(1);
            });

            @SuppressWarnings("unchecked")
            List<MissionRow> weeklyMissions = (List<MissionRow>) modelOf(result).get("weeklyMissions");
            assertThat(weeklyMissions).singleElement().satisfies(m -> {
                assertThat(m.active()).isFalse();
                assertThat(m.currentParticipants()).isEqualTo(1);
            });

            MissionOverview overview = (MissionOverview) modelOf(result).get("overview");
            assertThat(overview.activeMissions()).isEqualTo(1);
            assertThat(overview.todayCompleted()).isEqualTo(1);
            assertThat(overview.todayClaimed()).isEqualTo(1);
        }

        @Test
        @DisplayName("예전 방식 적립 기록을 유형별로 센다")
        void countsLegacyLogs() throws Exception {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 2L);

            MvcResult result = mockMvc.perform(get("/admin/missions")).andReturn();

            @SuppressWarnings("unchecked")
            List<LegacyLogCount> legacy = (List<LegacyLogCount>) modelOf(result).get("legacyLogs");
            assertThat(legacy).extracting(LegacyLogCount::label)
                    .containsExactly("출석", "오답노트 작성", "문제 복습", "복습노트 첫 완료");
            assertThat(legacy).extracting(LegacyLogCount::count).containsExactly(1L, 0L, 0L, 2L);
        }
    }
}
