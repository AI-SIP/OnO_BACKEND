package com.aisip.OnO.backend.admin.controller;

import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementEarned;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.CosmeticItemRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.CosmeticOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionDaily;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionOverview;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.SetSummary;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.SlotGroup;
import com.aisip.OnO.backend.admin.repository.AdminGrowthQueryRepository;
import com.aisip.OnO.backend.admin.repository.AdminGrowthQueryRepository.AchievementKeyStat;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.mission.service.MissionPeriodKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 성장 요소(미션, 업적, 치장) 현황. 전부 읽기 전용이다.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminGrowthController {

    private static final int RECENT_ACHIEVEMENT_LIMIT = 30;
    private static final int MISSION_TREND_DAYS = 30;

    private final AdminGrowthQueryRepository growthQueryRepository;

    @GetMapping("/cosmetics")
    public String cosmetics(@RequestParam(name = "slot", required = false) String slot, Model model) {
        List<CosmeticItemRow> items = growthQueryRepository.findCosmeticItems();

        Map<String, List<CosmeticItemRow>> bySlot = items.stream()
                .collect(Collectors.groupingBy(CosmeticItemRow::slot, LinkedHashMap::new, Collectors.toList()));

        // enum 순서(그리는 층 순서)대로 늘어놓고, enum 에 없는 자리(예전 배포에서 지운 값)는 맨 뒤에 둔다.
        List<SlotGroup> groups = new ArrayList<>();
        for (CosmeticSlot s : CosmeticSlot.values()) {
            List<CosmeticItemRow> slotItems = bySlot.remove(s.name());
            if (slotItems != null) {
                groups.add(toGroup(s.name(), s.getNameKo(), slotItems));
            }
        }
        bySlot.forEach((name, slotItems) -> groups.add(toGroup(name, name, slotItems)));

        CosmeticItemRow top = items.stream()
                .filter(i -> i.equippedUsers() > 0)
                .max(Comparator.comparingLong(CosmeticItemRow::equippedUsers))
                .orElse(null);

        CosmeticOverview overview = new CosmeticOverview(
                items.size(),
                items.stream().filter(CosmeticItemRow::active).count(),
                growthQueryRepository.countUsersWithLoadout(),
                top == null ? null : top.nameKo(),
                top == null ? 0 : top.equippedUsers()
        );

        List<SetSummary> sets = items.stream()
                .filter(i -> i.setId() != null)
                .collect(Collectors.groupingBy(CosmeticItemRow::setId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new SetSummary(
                        e.getKey(),
                        e.getValue().stream().map(CosmeticItemRow::setNameKo).filter(n -> n != null).findFirst().orElse(e.getKey()),
                        e.getValue().size(),
                        e.getValue().stream().mapToLong(CosmeticItemRow::equippedUsers).sum()))
                .toList();

        String selectedSlot = groups.stream().anyMatch(g -> g.slot().equals(slot)) ? slot : null;

        model.addAttribute("overview", overview);
        model.addAttribute("groups", groups);
        model.addAttribute("visibleGroups", selectedSlot == null
                ? groups
                : groups.stream().filter(g -> g.slot().equals(selectedSlot)).toList());
        model.addAttribute("selectedSlot", selectedSlot);
        model.addAttribute("maxSlotEquipped", groups.stream().mapToLong(SlotGroup::equippedUsers).max().orElse(0));
        model.addAttribute("sets", sets);
        return "admin-cosmetics";
    }

    @GetMapping("/achievements")
    public String achievements(Model model) {
        long totalUsers = growthQueryRepository.countLiveUsers();
        Map<String, AchievementKeyStat> stats = growthQueryRepository.countAchievementsByKey();

        List<AchievementRow> rows = Arrays.stream(Achievement.values())
                .map(a -> {
                    AchievementKeyStat stat = stats.get(a.getKey());
                    long earned = stat == null ? 0 : stat.earnedUsers();
                    return new AchievementRow(
                            a.getKey(),
                            a.getNameKo(),
                            a.getDescriptionKo(),
                            a.getThreshold(),
                            earned,
                            totalUsers == 0 ? 0 : (double) earned * 100 / totalUsers,
                            stat == null ? null : stat.lastEarnedAt());
                })
                .toList();

        // 키 대신 이름을 보여 준다. 지금 enum 에 없는 키면 저장된 키를 그대로 둔다.
        List<AchievementEarned> recent = growthQueryRepository.findRecentAchievements(RECENT_ACHIEVEMENT_LIMIT).stream()
                .map(e -> new AchievementEarned(
                        e.userId(),
                        e.userName(),
                        Achievement.fromKey(e.achievementName()).map(Achievement::getNameKo).orElse(e.achievementName()),
                        e.earnedAt()))
                .toList();

        LocalDateTime sevenDaysAgo = MissionPeriodKey.today().minusDays(6).atStartOfDay();
        model.addAttribute("overview", new AchievementOverview(
                growthQueryRepository.countEarnedAchievements(),
                growthQueryRepository.countUsersWithAchievement(),
                growthQueryRepository.countAchievementsEarnedSince(sevenDaysAgo),
                totalUsers));
        model.addAttribute("achievements", rows);
        model.addAttribute("recent", recent);
        return "admin-achievements";
    }

    @GetMapping("/missions")
    public String missions(Model model) {
        LocalDate today = MissionPeriodKey.today();
        String dailyKey = MissionPeriodKey.daily(today);
        String weeklyKey = MissionPeriodKey.weekly(today);

        List<MissionRow> missions = growthQueryRepository.findMissions(dailyKey, weeklyKey);

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        MissionOverview overview = new MissionOverview(
                growthQueryRepository.countActiveMissions(),
                growthQueryRepository.countMissionsCompletedBetween(todayStart, tomorrowStart),
                growthQueryRepository.countMissionsClaimedBetween(todayStart, tomorrowStart),
                growthQueryRepository.countMissionsClaimedBetween(today.minusDays(6).atStartOfDay(), tomorrowStart)
        );

        LocalDate trendStart = today.minusDays(MISSION_TREND_DAYS - 1L);
        Map<LocalDate, long[]> daily = growthQueryRepository.countMissionsDaily(trendStart, today);
        List<MissionDaily> trend = new ArrayList<>();
        for (LocalDate d = today; !d.isBefore(trendStart); d = d.minusDays(1)) {
            long[] v = daily.getOrDefault(d, new long[2]);
            trend.add(new MissionDaily(d, v[0], v[1]));
        }

        model.addAttribute("overview", overview);
        model.addAttribute("dailyMissions", missions.stream().filter(m -> !"WEEKLY".equals(m.category())).toList());
        model.addAttribute("weeklyMissions", missions.stream().filter(m -> "WEEKLY".equals(m.category())).toList());
        model.addAttribute("dailyKey", dailyKey);
        model.addAttribute("weeklyKey", weeklyKey);
        model.addAttribute("trend", trend);
        model.addAttribute("maxTrend", trend.stream().mapToLong(t -> Math.max(t.completed(), t.claimed())).max().orElse(0));
        model.addAttribute("legacyLogs", growthQueryRepository.countLegacyLogsSince(trendStart.atStartOfDay()));
        return "admin-missions";
    }

    private SlotGroup toGroup(String slot, String label, List<CosmeticItemRow> items) {
        return new SlotGroup(slot, label, items, items.stream().mapToLong(CosmeticItemRow::equippedUsers).sum());
    }
}
