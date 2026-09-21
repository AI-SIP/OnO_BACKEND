package com.aisip.OnO.backend.admin.repository;

import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.admin.dto.AdminUserRows;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.mission.entity.MissionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 유저 화면 전용 읽기 쿼리.
 *
 * <p>유저 한 명을 보려면 문제, 복습, 복습노트, 미션, 치장, 업적, 스터디룸까지 열 개 가까운 테이블을
 * 봐야 한다. 도메인 서비스마다 엔티티를 불러 모으면 연관을 따라가며 쿼리가 불어나서, 화면에 필요한
 * 열만 SQL 로 바로 읽는다. 쓰기는 하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    /** 정렬 파라미터는 사용자 입력이라 SQL 에 그대로 붙이지 않고 여기 있는 것만 받는다. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "u.created_at",
            "lastActiveAt", "u.last_active_at",
            "totalStudyLevel", "u.total_study_level",
            "problemCount", "problem_count",
            "solveCount", "solve_count",
            "practiceNoteCount", "note_count",
            "name", "u.name"
    );

    /** mission_log.mission_type 은 {@link MissionType} 의 ordinal 로 저장된다. */
    private static final Map<MissionType, String> MISSION_TYPE_LABELS = Map.of(
            MissionType.USER_LOGIN, "출석",
            MissionType.PROBLEM_WRITE, "오답노트 작성",
            MissionType.PROBLEM_PRACTICE, "문제 복습",
            MissionType.NOTE_PRACTICE, "복습노트 완료"
    );

    private static final int DETAIL_LIST_LIMIT = 300;

    private final NamedParameterJdbcTemplate jdbc;

    public static String resolveSortColumn(String sortBy) {
        return SORT_COLUMNS.getOrDefault(sortBy, SORT_COLUMNS.get("createdAt"));
    }

    // ---------- 목록 ----------

    public List<AdminUserRows.ListRow> findUsers(String q, String platform, String sortBy, String direction, long offset, int limit) {
        MapSqlParameterSource params = searchParams(q, platform)
                .addValue("limit", limit)
                .addValue("offset", offset);
        String order = resolveSortColumn(sortBy) + ("asc".equalsIgnoreCase(direction) ? " ASC" : " DESC");

        String sql = """
                SELECT u.id, u.name, u.email, u.platform, u.total_study_level, u.total_study_point,
                       u.last_active_at, u.created_at,
                       COALESCE(p.cnt, 0) AS problem_count,
                       COALESCE(s.cnt, 0) AS solve_count,
                       COALESCE(n.cnt, 0) AS note_count
                FROM `user` u
                LEFT JOIN (SELECT user_id, COUNT(*) cnt FROM problem WHERE deleted_at IS NULL GROUP BY user_id) p
                       ON p.user_id = u.id
                LEFT JOIN (SELECT user_id, COUNT(*) cnt FROM problem_solve WHERE deleted_at IS NULL GROUP BY user_id) s
                       ON s.user_id = u.id
                LEFT JOIN (SELECT user_id, COUNT(*) cnt FROM practice_note WHERE deleted_at IS NULL GROUP BY user_id) n
                       ON n.user_id = u.id
                WHERE u.deleted_at IS NULL
                """ + searchCondition(q, platform) + """
                ORDER BY %s, u.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(order);

        return jdbc.query(sql, params, (rs, i) -> new AdminUserRows.ListRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("platform"),
                rs.getLong("total_study_level"),
                rs.getLong("total_study_point"),
                rs.getLong("problem_count"),
                rs.getLong("solve_count"),
                rs.getLong("note_count"),
                toDateTime(rs.getTimestamp("last_active_at")),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    public long countUsers(String q, String platform) {
        String sql = "SELECT COUNT(*) FROM `user` u WHERE u.deleted_at IS NULL " + searchCondition(q, platform);
        Long count = jdbc.queryForObject(sql, searchParams(q, platform), Long.class);
        return count == null ? 0 : count;
    }

    public List<String> findPlatforms() {
        return jdbc.queryForList("""
                SELECT DISTINCT UPPER(platform) FROM `user`
                WHERE deleted_at IS NULL AND platform IS NOT NULL AND platform <> ''
                ORDER BY 1
                """, Map.of(), String.class);
    }

    public AdminUserRows.Summary summarize(LocalDateTime todayStart, LocalDateTime weekStart) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("todayStart", todayStart)
                .addValue("weekStart", weekStart);
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(created_at >= :todayStart), 0) AS today,
                       COALESCE(SUM(UPPER(platform) = 'GUEST'), 0) AS guests,
                       (SELECT COUNT(DISTINCT user_id) FROM mission_log
                         WHERE deleted_at IS NULL AND mission_type = 0 AND created_at >= :weekStart) AS active7
                FROM `user`
                WHERE deleted_at IS NULL
                """, params, (rs, i) -> new AdminUserRows.Summary(
                rs.getLong("total"), rs.getLong("today"), rs.getLong("guests"), rs.getLong("active7")));
    }

    private String searchCondition(String q, String platform) {
        StringBuilder where = new StringBuilder();
        if (q != null) {
            where.append(" AND (u.name LIKE :like OR u.email LIKE :like");
            if (parseId(q) != null) {
                where.append(" OR u.id = :qid");
            }
            where.append(")");
        }
        if (platform != null) {
            where.append(" AND UPPER(u.platform) = :platform");
        }
        return where.append('\n').toString();
    }

    private MapSqlParameterSource searchParams(String q, String platform) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (q != null) {
            params.addValue("like", "%" + escapeLike(q) + "%");
            params.addValue("qid", parseId(q));
        }
        if (platform != null) {
            params.addValue("platform", platform.toUpperCase());
        }
        return params;
    }

    private static Long parseId(String q) {
        try {
            return Long.parseLong(q);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 이름에 % 나 _ 가 들어간 유저를 찾을 때 와일드카드로 먹히지 않게 한다. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------- 상세 ----------

    public Optional<AdminUserRows.Profile> findProfile(Long userId) {
        List<AdminUserRows.Profile> rows = jdbc.query("""
                SELECT id, name, email, platform, profile_image_url, notification_enabled,
                       last_active_at, last_notified_at, created_at,
                       attendance_level, attendance_point, note_write_level, note_write_point,
                       problem_practice_level, problem_practice_point, note_practice_level, note_practice_point,
                       total_study_level, total_study_point
                FROM `user`
                WHERE id = :userId AND deleted_at IS NULL
                """, Map.of("userId", userId), (rs, i) -> new AdminUserRows.Profile(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("platform"),
                rs.getString("profile_image_url"),
                rs.getBoolean("notification_enabled"),
                toDateTime(rs.getTimestamp("last_active_at")),
                toDate(rs.getDate("last_notified_at")),
                toDateTime(rs.getTimestamp("created_at")),
                level(rs, "attendance"),
                level(rs, "note_write"),
                level(rs, "problem_practice"),
                level(rs, "note_practice"),
                level(rs, "total_study")
        ));
        return rows.stream().findFirst();
    }

    public AdminUserRows.Counts countOwnedData(Long userId) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM problem WHERE user_id = :userId AND deleted_at IS NULL) AS problems,
                  (SELECT COUNT(*) FROM problem_solve WHERE user_id = :userId AND deleted_at IS NULL) AS solves,
                  (SELECT COUNT(*) FROM problem_solve
                    WHERE user_id = :userId AND deleted_at IS NULL AND answer_status = 'CORRECT') AS correct_solves,
                  (SELECT COUNT(*) FROM practice_note WHERE user_id = :userId AND deleted_at IS NULL) AS notes,
                  (SELECT COUNT(*) FROM folder WHERE user_id = :userId AND deleted_at IS NULL) AS folders,
                  (SELECT COUNT(*) FROM tag WHERE user_id = :userId AND deleted_at IS NULL) AS tags,
                  (SELECT COUNT(*) FROM study_room_member WHERE user_id = :userId AND deleted_at IS NULL) AS rooms,
                  (SELECT COUNT(*) FROM user_achievement WHERE user_id = :userId) AS achievements,
                  (SELECT COUNT(*) FROM fcm_token WHERE user_id = :userId AND deleted_at IS NULL) AS fcm_tokens,
                  (SELECT COUNT(DISTINCT DATE(created_at)) FROM mission_log
                    WHERE user_id = :userId AND deleted_at IS NULL AND mission_type = 0) AS login_days
                """, Map.of("userId", userId), (rs, i) -> new AdminUserRows.Counts(
                rs.getLong("problems"),
                rs.getLong("solves"),
                rs.getLong("correct_solves"),
                rs.getLong("notes"),
                rs.getLong("folders"),
                rs.getLong("tags"),
                rs.getLong("rooms"),
                rs.getLong("achievements"),
                rs.getLong("fcm_tokens"),
                rs.getLong("login_days")
        ));
    }

    /** 연속 출석을 계산하려고 최근 출석한 날짜를 최신순으로 가져온다. */
    public List<LocalDate> findRecentLoginDates(Long userId, int limit) {
        return jdbc.query("""
                SELECT DISTINCT DATE(created_at) AS d FROM mission_log
                WHERE user_id = :userId AND deleted_at IS NULL AND mission_type = 0
                ORDER BY d DESC
                LIMIT :limit
                """, new MapSqlParameterSource("userId", userId).addValue("limit", limit),
                (rs, i) -> rs.getDate("d").toLocalDate());
    }

    public List<AdminUserRows.ProblemRow> findProblems(Long userId) {
        return jdbc.query("""
                SELECT p.id, p.memo, p.reference, f.name AS folder_name, a.status, a.subject,
                       COALESCE(s.cnt, 0) AS solve_count, p.next_review_at, p.created_at
                FROM problem p
                LEFT JOIN folder f ON f.id = p.folder_id AND f.deleted_at IS NULL
                LEFT JOIN problem_analysis a ON a.problem_id = p.id AND a.deleted_at IS NULL
                LEFT JOIN (SELECT problem_id, COUNT(*) cnt FROM problem_solve
                            WHERE user_id = :userId AND deleted_at IS NULL GROUP BY problem_id) s
                       ON s.problem_id = p.id
                WHERE p.user_id = :userId AND p.deleted_at IS NULL
                ORDER BY p.created_at DESC, p.id DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.ProblemRow(
                rs.getLong("id"),
                rs.getString("memo"),
                rs.getString("reference"),
                rs.getString("folder_name"),
                rs.getString("status"),
                rs.getString("subject"),
                rs.getLong("solve_count"),
                toDate(rs.getDate("next_review_at")),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    public List<AdminUserRows.SolveRow> findSolves(Long userId) {
        return jdbc.query("""
                SELECT s.id, s.problem_id, p.reference, s.answer_status, s.time_spent_seconds,
                       s.reflection, s.mood_emoji_key, s.practiced_at
                FROM problem_solve s
                LEFT JOIN problem p ON p.id = s.problem_id
                WHERE s.user_id = :userId AND s.deleted_at IS NULL
                ORDER BY s.practiced_at DESC, s.id DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.SolveRow(
                rs.getLong("id"),
                rs.getLong("problem_id"),
                rs.getString("reference"),
                rs.getString("answer_status"),
                nullableInt(rs, "time_spent_seconds"),
                rs.getString("reflection"),
                rs.getString("mood_emoji_key"),
                toDateTime(rs.getTimestamp("practiced_at"))
        ));
    }

    public List<AdminUserRows.PracticeNoteRow> findPracticeNotes(Long userId) {
        return jdbc.query("""
                SELECT n.id, n.title, COALESCE(m.cnt, 0) AS problem_count, n.practice_count,
                       n.last_solved_at, n.repeat_type, n.created_at
                FROM practice_note n
                LEFT JOIN (SELECT practice_note_id, COUNT(*) cnt FROM problem_practice_note_mapping
                            WHERE deleted_at IS NULL GROUP BY practice_note_id) m
                       ON m.practice_note_id = n.id
                WHERE n.user_id = :userId AND n.deleted_at IS NULL
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.PracticeNoteRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getLong("problem_count"),
                (Long) rs.getObject("practice_count", Long.class),
                toDateTime(rs.getTimestamp("last_solved_at")),
                rs.getString("repeat_type"),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    public List<AdminUserRows.FolderRow> findFolders(Long userId) {
        return jdbc.query("""
                SELECT f.id, f.name, parent.name AS parent_name, COALESCE(p.cnt, 0) AS problem_count, f.created_at
                FROM folder f
                LEFT JOIN folder parent ON parent.id = f.parent_folder_id
                LEFT JOIN (SELECT folder_id, COUNT(*) cnt FROM problem
                            WHERE user_id = :userId AND deleted_at IS NULL GROUP BY folder_id) p
                       ON p.folder_id = f.id
                WHERE f.user_id = :userId AND f.deleted_at IS NULL
                ORDER BY f.created_at, f.id
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.FolderRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("parent_name"),
                rs.getLong("problem_count"),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    public List<AdminUserRows.TagRow> findTags(Long userId) {
        return jdbc.query("""
                SELECT t.id, t.name, COUNT(m.id) AS problem_count
                FROM tag t
                LEFT JOIN problem_tag_mapping m ON m.tag_id = t.id AND m.deleted_at IS NULL
                WHERE t.user_id = :userId AND t.deleted_at IS NULL
                GROUP BY t.id, t.name
                ORDER BY problem_count DESC, t.name
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.TagRow(
                rs.getLong("id"), rs.getString("name"), rs.getLong("problem_count")));
    }

    public List<AdminUserRows.MissionProgressRow> findMissionProgress(Long userId) {
        return jdbc.query("""
                SELECT d.code, d.title, d.category, mp.period_key, mp.current_value, mp.target_snapshot,
                       mp.completed_at, mp.claimed_at,
                       COALESCE(mp.reward_type_snapshot, d.reward_type) AS reward_type,
                       COALESCE(mp.reward_value_snapshot, d.reward_value) AS reward_value
                FROM mission_progress mp
                LEFT JOIN mission_definition d ON d.id = mp.mission_id
                WHERE mp.user_id = :userId AND mp.deleted_at IS NULL
                ORDER BY mp.period_key DESC, d.sort_order, mp.id DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.MissionProgressRow(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("period_key"),
                rs.getInt("current_value"),
                rs.getInt("target_snapshot"),
                toDateTime(rs.getTimestamp("completed_at")),
                toDateTime(rs.getTimestamp("claimed_at")),
                rs.getString("reward_type"),
                nullableInt(rs, "reward_value")
        ));
    }

    public List<AdminUserRows.MissionLogRow> findMissionLogs(Long userId) {
        MissionType[] types = MissionType.values();
        return jdbc.query("""
                SELECT id, mission_type, point, reference_id, created_at
                FROM mission_log
                WHERE user_id = :userId AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> {
            int ordinal = rs.getInt("mission_type");
            String label = ordinal >= 0 && ordinal < types.length
                    ? MISSION_TYPE_LABELS.getOrDefault(types[ordinal], types[ordinal].name())
                    : String.valueOf(ordinal);
            return new AdminUserRows.MissionLogRow(
                    rs.getLong("id"),
                    label,
                    (Long) rs.getObject("point", Long.class),
                    (Long) rs.getObject("reference_id", Long.class),
                    toDateTime(rs.getTimestamp("created_at"))
            );
        });
    }

    public List<AdminUserRows.StudyRoomRow> findStudyRooms(Long userId) {
        return jdbc.query("""
                SELECT r.id, r.name, m.role, m.weekly_goal, m.created_at,
                       (SELECT COUNT(*) FROM study_room_member x
                         WHERE x.room_id = r.id AND x.deleted_at IS NULL) AS member_count
                FROM study_room_member m
                JOIN study_room r ON r.id = m.room_id AND r.deleted_at IS NULL
                WHERE m.user_id = :userId AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC
                LIMIT :limit
                """, detailParams(userId), (rs, i) -> new AdminUserRows.StudyRoomRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getLong("member_count"),
                nullableInt(rs, "weekly_goal"),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    /** 착용 중인 치장. 슬롯 순서는 화면에 겹쳐 그리는 순서(layer_order)를 따른다. */
    public List<AdminUserRows.CosmeticRow> findEquippedCosmetics(Long userId) {
        RowMapper<AdminUserRows.CosmeticRow> mapper = (rs, i) -> {
            String slot = rs.getString("slot");
            return new AdminUserRows.CosmeticRow(
                    slot,
                    slotName(slot),
                    rs.getString("item_key"),
                    rs.getString("name_ko"),
                    rs.getString("set_name_ko"),
                    toDateTime(rs.getTimestamp("updated_at"))
            );
        };
        List<AdminUserRows.CosmeticRow> rows = jdbc.query("""
                SELECT l.slot, l.item_key, c.name_ko, c.set_name_ko, l.updated_at
                FROM user_cosmetic_loadout l
                LEFT JOIN cosmetic_item c ON c.item_key = l.item_key
                WHERE l.user_id = :userId
                """, Map.of("userId", userId), mapper);
        return rows.stream()
                .sorted(java.util.Comparator.comparingInt(row -> slotOrder(row.slot())))
                .toList();
    }

    /** 업적 이름은 DB 가 아니라 {@link Achievement} 에 있다. 모르는 키도 버리지 않고 키 그대로 보여 준다. */
    public List<AdminUserRows.AchievementRow> findAchievements(Long userId) {
        return jdbc.query("""
                SELECT achievement_key, earned_at FROM user_achievement
                WHERE user_id = :userId
                ORDER BY earned_at DESC
                """, Map.of("userId", userId), (rs, i) -> {
            String key = rs.getString("achievement_key");
            Optional<Achievement> achievement = Achievement.fromKey(key);
            return new AdminUserRows.AchievementRow(
                    key,
                    achievement.map(Achievement::getNameKo).orElse(key),
                    achievement.map(Achievement::getDescriptionKo).orElse(null),
                    toDateTime(rs.getTimestamp("earned_at"))
            );
        });
    }

    public List<AdminUserRows.MoodRow> findMoods(Long userId, int limit) {
        return jdbc.query("""
                SELECT study_date, emoji_key FROM learning_calendar_mood
                WHERE user_id = :userId AND deleted_at IS NULL
                ORDER BY study_date DESC
                LIMIT :limit
                """, new MapSqlParameterSource("userId", userId).addValue("limit", limit),
                (rs, i) -> new AdminUserRows.MoodRow(toDate(rs.getDate("study_date")), rs.getString("emoji_key")));
    }

    // ---------- 변환 ----------

    private static MapSqlParameterSource detailParams(Long userId) {
        return new MapSqlParameterSource("userId", userId).addValue("limit", DETAIL_LIST_LIMIT);
    }

    public static int detailListLimit() {
        return DETAIL_LIST_LIMIT;
    }

    private static AdminUserRows.Level level(ResultSet rs, String prefix) throws SQLException {
        return new AdminUserRows.Level(
                (Long) rs.getObject(prefix + "_level", Long.class),
                (Long) rs.getObject(prefix + "_point", Long.class)
        );
    }

    /** COALESCE 나 드라이버 설정에 따라 정수 컬럼이 Long 으로 올 수 있어 숫자로 받아 변환한다. */
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static String slotName(String slot) {
        try {
            return CosmeticSlot.valueOf(slot).getNameKo();
        } catch (IllegalArgumentException | NullPointerException e) {
            return slot;
        }
    }

    private static int slotOrder(String slot) {
        try {
            return CosmeticSlot.valueOf(slot).getLayerOrder();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static LocalDate toDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
