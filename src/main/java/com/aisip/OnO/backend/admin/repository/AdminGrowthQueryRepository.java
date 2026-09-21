package com.aisip.OnO.backend.admin.repository;

import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.AchievementEarned;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.CosmeticItemRow;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.LegacyLogCount;
import com.aisip.OnO.backend.admin.dto.AdminGrowthViews.MissionRow;
import com.aisip.OnO.backend.mission.entity.MissionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static com.aisip.OnO.backend.admin.repository.AdminSqlFilters.countedUser;
import static com.aisip.OnO.backend.admin.repository.AdminSqlFilters.excludeTestUsers;
/**
 * 관리자 성장 화면(미션, 업적, 치장) 전용 조회.
 *
 * <p>유저 수는 탈퇴하지 않았고 관리자 계정이 아닌 사람만 센다. 관리자 계정은 부팅할 때 자동으로
 * 만들어져서 비율의 분모에 끼면 실제 사용자 비율이 조금씩 틀어진다.
 */
@Repository
@RequiredArgsConstructor
public class AdminGrowthQueryRepository {

    /** 집계에 넣을 유저. 탈퇴한 계정과 게스트, 관리자 계정은 뺀다. */
    private static final String LIVE_USER = "u.deleted_at IS NULL" + countedUser("u");

    private final NamedParameterJdbcTemplate jdbc;

    public long countLiveUsers() {
        return count("SELECT COUNT(*) FROM user u WHERE " + LIVE_USER, new MapSqlParameterSource());
    }

    // ---------- 치장 ----------

    /** 아이템마다 지금 착용 중인 유저 수. 착용 기록은 아이템 키로만 이어져 있어 키로 붙인다. */
    public List<CosmeticItemRow> findCosmeticItems() {
        return jdbc.query("""
                SELECT ci.item_key, ci.slot, ci.name_ko, ci.image_url, ci.required_level, ci.required_ability,
                       ci.set_id, ci.set_name_ko, ci.full_body, ci.active,
                       COUNT(u.id) AS equipped_users
                FROM cosmetic_item ci
                LEFT JOIN user_cosmetic_loadout l ON l.item_key = ci.item_key AND l.slot = ci.slot
                LEFT JOIN user u ON u.id = l.user_id AND %s
                GROUP BY ci.id, ci.item_key, ci.slot, ci.name_ko, ci.image_url, ci.required_level,
                         ci.required_ability, ci.set_id, ci.set_name_ko, ci.full_body, ci.active
                ORDER BY ci.required_level IS NULL DESC, ci.required_level ASC, ci.id ASC
                """.formatted(LIVE_USER), new MapSqlParameterSource(), (rs, i) -> {
            int level = rs.getInt("required_level");
            Integer requiredLevel = rs.wasNull() ? null : level;
            return new CosmeticItemRow(
                    rs.getString("item_key"),
                    rs.getString("slot"),
                    rs.getString("name_ko"),
                    rs.getString("image_url"),
                    requiredLevel,
                    AdminStudyRoomLabels.ability(rs.getString("required_ability")),
                    rs.getString("set_id"),
                    rs.getString("set_name_ko"),
                    rs.getBoolean("full_body"),
                    rs.getBoolean("active"),
                    rs.getLong("equipped_users"));
        });
    }

    public long countUsersWithLoadout() {
        return count("""
                SELECT COUNT(DISTINCT l.user_id)
                FROM user_cosmetic_loadout l
                JOIN user u ON u.id = l.user_id AND %s
                """.formatted(LIVE_USER), new MapSqlParameterSource());
    }

    // ---------- 업적 ----------

    public record AchievementKeyStat(long earnedUsers, LocalDateTime lastEarnedAt) {
    }

    /** 업적 키별 획득 유저 수와 마지막 획득 시각. */
    public Map<String, AchievementKeyStat> countAchievementsByKey() {
        Map<String, AchievementKeyStat> result = new HashMap<>();
        jdbc.query("""
                SELECT ua.achievement_key, COUNT(*) AS earned, MAX(ua.earned_at) AS last_earned_at
                FROM user_achievement ua
                JOIN user u ON u.id = ua.user_id AND %s
                GROUP BY ua.achievement_key
                """.formatted(LIVE_USER), new MapSqlParameterSource(), rs -> {
            result.put(rs.getString("achievement_key"), new AchievementKeyStat(
                    rs.getLong("earned"), rs.getObject("last_earned_at", LocalDateTime.class)));
        });
        return result;
    }

    public long countEarnedAchievements() {
        return count("""
                SELECT COUNT(*) FROM user_achievement ua JOIN user u ON u.id = ua.user_id AND %s
                """.formatted(LIVE_USER), new MapSqlParameterSource());
    }

    public long countUsersWithAchievement() {
        return count("""
                SELECT COUNT(DISTINCT ua.user_id) FROM user_achievement ua JOIN user u ON u.id = ua.user_id AND %s
                """.formatted(LIVE_USER), new MapSqlParameterSource());
    }

    public long countAchievementsEarnedSince(LocalDateTime since) {
        return count("""
                SELECT COUNT(*) FROM user_achievement ua JOIN user u ON u.id = ua.user_id AND %s
                WHERE ua.earned_at >= :since
                """.formatted(LIVE_USER), new MapSqlParameterSource("since", since));
    }

    /** 최근에 받은 업적. 이름은 enum 에서 붙이도록 키를 그대로 돌려준다. */
    public List<AchievementEarned> findRecentAchievements(int limit) {
        return jdbc.query("""
                SELECT ua.user_id, u.name, ua.achievement_key, ua.earned_at
                FROM user_achievement ua
                JOIN user u ON u.id = ua.user_id AND %s
                ORDER BY ua.earned_at DESC
                LIMIT :limit
                """.formatted(LIVE_USER), new MapSqlParameterSource("limit", limit), (rs, i) -> new AchievementEarned(
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("achievement_key"),
                rs.getObject("earned_at", LocalDateTime.class)
        ));
    }

    // ---------- 미션 ----------

    /**
     * 미션 정의와 진행 집계.
     *
     * <p>이번 기간은 {@code period_key} 로 가른다. 일일 미션 키(yyyy-MM-dd)와 주간 미션 키(yyyy-Www)는
     * 모양이 달라서 두 키를 한 IN 조건에 넣어도 서로 섞이지 않는다.
     */
    public List<MissionRow> findMissions(String dailyKey, String weeklyKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dailyKey", dailyKey)
                .addValue("weeklyKey", weeklyKey);
        return jdbc.query("""
                SELECT d.id, d.code, d.title, d.description, d.category, d.metric, d.target,
                       d.reward_type, d.reward_value, d.active,
                  COALESCE(SUM(CASE WHEN mp.period_key IN (:dailyKey, :weeklyKey) THEN 1 ELSE 0 END), 0) AS cur_participants,
                  COALESCE(SUM(CASE WHEN mp.period_key IN (:dailyKey, :weeklyKey) AND mp.completed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS cur_completed,
                  COALESCE(SUM(CASE WHEN mp.period_key IN (:dailyKey, :weeklyKey) AND mp.claimed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS cur_claimed,
                  COUNT(mp.id) AS total_participants,
                  COALESCE(SUM(CASE WHEN mp.completed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS total_completed,
                  COALESCE(SUM(CASE WHEN mp.claimed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS total_claimed
                FROM mission_definition d
                LEFT JOIN mission_progress mp ON mp.mission_id = d.id AND mp.deleted_at IS NULL
                """ + excludeTestUsers("mp.user_id") + """
                WHERE d.deleted_at IS NULL
                GROUP BY d.id, d.code, d.title, d.description, d.category, d.metric, d.target,
                         d.reward_type, d.reward_value, d.active, d.sort_order
                ORDER BY d.category ASC, d.sort_order ASC, d.id ASC
                """, params, (rs, i) -> {
            String category = rs.getString("category");
            return new MissionRow(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("title"),
                    rs.getString("description"),
                    category,
                    metricLabel(rs.getString("metric")),
                    rs.getInt("target"),
                    rewardLabel(rs.getString("reward_type"), rs.getInt("reward_value")),
                    rs.getBoolean("active"),
                    "WEEKLY".equals(category) ? weeklyKey : dailyKey,
                    rs.getLong("cur_participants"),
                    rs.getLong("cur_completed"),
                    rs.getLong("cur_claimed"),
                    rs.getLong("total_participants"),
                    rs.getLong("total_completed"),
                    rs.getLong("total_claimed"));
        });
    }

    public long countActiveMissions() {
        return count("SELECT COUNT(*) FROM mission_definition WHERE deleted_at IS NULL AND active = TRUE",
                new MapSqlParameterSource());
    }

    public long countMissionsCompletedBetween(LocalDateTime from, LocalDateTime to) {
        return count("""
                SELECT COUNT(*) FROM mission_progress
                WHERE deleted_at IS NULL AND completed_at >= :from AND completed_at < :to
                """ + excludeTestUsers("user_id"), new MapSqlParameterSource().addValue("from", from).addValue("to", to));
    }

    public long countMissionsClaimedBetween(LocalDateTime from, LocalDateTime to) {
        return count("""
                SELECT COUNT(*) FROM mission_progress
                WHERE deleted_at IS NULL AND claimed_at >= :from AND claimed_at < :to
                """ + excludeTestUsers("user_id"), new MapSqlParameterSource().addValue("from", from).addValue("to", to));
    }

    /** 날짜별 완료 건수와 수령 건수. 두 시각이 다른 날일 수 있어 따로 센다. */
    public Map<LocalDate, long[]> countMissionsDaily(LocalDate from, LocalDate toInclusive) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from.atStartOfDay())
                .addValue("to", toInclusive.plusDays(1).atStartOfDay());
        Map<LocalDate, long[]> result = new HashMap<>();
        jdbc.query("""
                SELECT DATE(completed_at) AS d, COUNT(*) AS c FROM mission_progress
                WHERE deleted_at IS NULL AND completed_at >= :from AND completed_at < :to
                """ + excludeTestUsers("user_id") + """
                GROUP BY DATE(completed_at)
                """, params, rs -> {
            result.computeIfAbsent(rs.getObject("d", LocalDate.class), k -> new long[2])[0] = rs.getLong("c");
        });
        jdbc.query("""
                SELECT DATE(claimed_at) AS d, COUNT(*) AS c FROM mission_progress
                WHERE deleted_at IS NULL AND claimed_at >= :from AND claimed_at < :to
                """ + excludeTestUsers("user_id") + """
                GROUP BY DATE(claimed_at)
                """, params, rs -> {
            result.computeIfAbsent(rs.getObject("d", LocalDate.class), k -> new long[2])[1] = rs.getLong("c");
        });
        return result;
    }

    /** 예전 방식 적립(mission_log) 을 유형별로 센다. mission_type 은 enum 순서값으로 저장돼 있다. */
    public List<LegacyLogCount> countLegacyLogsSince(LocalDateTime since) {
        Map<Integer, long[]> byType = new HashMap<>();
        jdbc.query("""
                SELECT mission_type, COUNT(*) AS c, COALESCE(SUM(point), 0) AS p
                FROM mission_log
                WHERE deleted_at IS NULL AND created_at >= :since
                """ + excludeTestUsers("user_id") + """
                GROUP BY mission_type
                """, new MapSqlParameterSource("since", since), rs -> {
            byType.put(rs.getInt("mission_type"), new long[]{rs.getLong("c"), rs.getLong("p")});
        });
        return java.util.Arrays.stream(MissionType.values())
                .map(type -> {
                    long[] v = byType.getOrDefault(type.ordinal(), new long[2]);
                    return new LegacyLogCount(legacyLabel(type), v[0], v[1]);
                })
                .toList();
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static String metricLabel(String metric) {
        if (metric == null) return "-";
        return switch (metric) {
            case "LOGIN_DAY" -> "출석";
            case "PROBLEM_CREATED" -> "오답노트 작성";
            case "SOLVE_RECORDED" -> "복습 기록";
            case "SOLVE_CORRECT" -> "복습 정답";
            case "PRACTICE_NOTE_COMPLETED" -> "복습노트 완료";
            case "MOOD_LOGGED" -> "기분 기록";
            default -> metric;
        };
    }

    private static String rewardLabel(String rewardType, int rewardValue) {
        if ("XP".equals(rewardType)) {
            return rewardValue + " XP";
        }
        return (rewardType == null ? "" : rewardType + " ") + rewardValue;
    }

    private static String legacyLabel(MissionType type) {
        return switch (type) {
            case USER_LOGIN -> "출석";
            case PROBLEM_WRITE -> "오답노트 작성";
            case PROBLEM_PRACTICE -> "문제 복습";
            case NOTE_PRACTICE -> "복습노트 첫 완료";
        };
    }
}
