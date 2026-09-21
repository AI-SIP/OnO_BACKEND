package com.aisip.OnO.backend.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 성장 화면(미션, 업적, 치장)이 쓰는 조회 결과.
 */
public final class AdminGrowthViews {

    private AdminGrowthViews() {
    }

    // ---------- 치장 ----------

    public record CosmeticOverview(
            long totalItems,
            long activeItems,
            long usersWithLoadout,
            String topItemName,
            long topItemEquipped
    ) {
    }

    public record CosmeticItemRow(
            String itemKey,
            String slot,
            String nameKo,
            String imageUrl,
            Integer requiredLevel,
            String requiredAbilityLabel,
            String setId,
            String setNameKo,
            boolean fullBody,
            boolean active,
            long equippedUsers
    ) {
        /** 번들 안 에셋(assets/...)은 서버에서 띄울 수 없어서, 절대 URL 일 때만 이미지를 그린다. */
        public boolean hasRemoteImage() {
            return imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"));
        }

        public String unlockLabel() {
            if (requiredLevel == null) {
                return "기본 지급";
            }
            return requiredAbilityLabel + " Lv." + requiredLevel;
        }
    }

    public record SlotGroup(String slot, String label, List<CosmeticItemRow> items, long equippedUsers) {
    }

    public record SetSummary(String setId, String setNameKo, long itemCount, long equippedUsers) {
    }

    // ---------- 업적 ----------

    public record AchievementOverview(
            long totalEarned,
            long usersWithAny,
            long earnedLast7Days,
            long totalUsers
    ) {
    }

    public record AchievementRow(
            String key,
            String nameKo,
            String descriptionKo,
            int threshold,
            long earnedUsers,
            double rate,
            LocalDateTime lastEarnedAt
    ) {
    }

    public record AchievementEarned(Long userId, String userName, String achievementName, LocalDateTime earnedAt) {
    }

    // ---------- 미션 ----------

    public record MissionOverview(
            long activeMissions,
            long todayCompleted,
            long todayClaimed,
            long claimedLast7Days
    ) {
    }

    public record MissionRow(
            Long id,
            String code,
            String title,
            String description,
            String category,
            String metricLabel,
            int target,
            String rewardLabel,
            boolean active,
            String currentPeriodKey,
            long currentParticipants,
            long currentCompleted,
            long currentClaimed,
            long totalParticipants,
            long totalCompleted,
            long totalClaimed
    ) {
        /** 이번 기간에 한 번이라도 진행한 사람 가운데 목표를 채운 비율. */
        public double currentCompletionRate() {
            return currentParticipants == 0 ? 0 : (double) currentCompleted * 100 / currentParticipants;
        }

        public double totalCompletionRate() {
            return totalParticipants == 0 ? 0 : (double) totalCompleted * 100 / totalParticipants;
        }
    }

    public record MissionDaily(LocalDate date, long completed, long claimed) {
    }

    public record LegacyLogCount(String label, long count, long points) {
    }
}
