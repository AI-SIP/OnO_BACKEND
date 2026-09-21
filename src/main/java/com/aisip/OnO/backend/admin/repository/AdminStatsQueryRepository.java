package com.aisip.OnO.backend.admin.repository;

import com.aisip.OnO.backend.admin.dto.AdminStatsDto.LabelCount;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RankRow;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentFeedback;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentProblem;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentSolve;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.RecentUser;
import com.aisip.OnO.backend.admin.dto.AdminStatsDto.Retention;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.aisip.OnO.backend.admin.repository.AdminSqlFilters.countedUser;
import static com.aisip.OnO.backend.admin.repository.AdminSqlFilters.excludeTestUsers;

/**
 * 관리자 통계와 홈 화면 전용 조회.
 *
 * <p>여러 도메인 테이블을 가로질러 세기만 하는 쿼리라 엔티티를 거치지 않고 SQL 로 바로 센다.
 * 기간 조건은 모두 {@code col >= :start AND col < :end} 꼴이다. {@code DATE(col) BETWEEN} 으로 쓰면
 * 컬럼에 함수가 씌워져 인덱스를 못 탄다.
 *
 * <p>소프트 삭제를 쓰는 테이블은 엔티티의 {@code @SQLRestriction} 이 여기에는 적용되지 않으므로
 * {@code deleted_at IS NULL} 을 직접 붙인다.
 *
 * <p>숫자를 세는 쿼리는 모두 게스트와 관리자 계정을 뺀다({@link AdminSqlFilters}). 최근 목록은 그대로 둔다.
 */
@Repository
@RequiredArgsConstructor
public class AdminStatsQueryRepository {

    /** mission_log.mission_type 은 enum 순서값으로 저장된다. MissionType 선언 순서와 같다. */
    private static final int USER_LOGIN = 0;
    private static final int NOTE_PRACTICE = 3;

    private final NamedParameterJdbcTemplate jdbc;

    // ---------- 일별 추이 ----------

    public Map<LocalDate, Long> dailyActiveUsers(LocalDate start, LocalDate end) {
        return daily("""
                SELECT DATE(created_at) d, COUNT(DISTINCT user_id) c FROM mission_log
                WHERE mission_type = :login AND deleted_at IS NULL AND created_at >= :start AND created_at < :end
                """ + excludeTestUsers("user_id") + """
                GROUP BY DATE(created_at)
                """, start, end, range(start, end).addValue("login", USER_LOGIN));
    }

    public Map<LocalDate, Long> dailyNewUsers(LocalDate start, LocalDate end) {
        return dailyCount("`user`", "created_at", start, end);
    }

    public Map<LocalDate, Long> dailyProblems(LocalDate start, LocalDate end) {
        return dailyCount("problem", "created_at", start, end);
    }

    /** 실제 복습 기록. mission_log 의 NOTE_PRACTICE 는 복습노트를 처음 끝낸 날만 남아서 복습량을 못 보여 준다. */
    public Map<LocalDate, Long> dailySolves(LocalDate start, LocalDate end) {
        return dailyCount("problem_solve", "practiced_at", start, end);
    }

    public Map<LocalDate, Long> dailyPracticeNotes(LocalDate start, LocalDate end) {
        return dailyCount("practice_note", "created_at", start, end);
    }

    public Map<LocalDate, Long> dailyMissionsCompleted(LocalDate start, LocalDate end) {
        return dailyCount("mission_progress", "completed_at", start, end);
    }

    /** 스터디룸에서 실제로 무언가를 남긴 건수. 문제 공유와 댓글을 합친다. */
    public Map<LocalDate, Long> dailyRoomActivity(LocalDate start, LocalDate end) {
        return daily("""
                SELECT d, SUM(c) c FROM (
                    SELECT DATE(created_at) d, COUNT(*) c FROM study_room_shared_problem
                    WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end
                    """ + excludeTestUsers("shared_by_user_id") + """
                    GROUP BY DATE(created_at)
                    UNION ALL
                    SELECT DATE(created_at) d, COUNT(*) c FROM study_room_shared_problem_comment
                    WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end
                    """ + excludeTestUsers("author_id") + """
                    GROUP BY DATE(created_at)
                ) t GROUP BY d
                """, start, end, range(start, end));
    }

    // ---------- 사용자 ----------

    public long countUsers() {
        return count("SELECT COUNT(*) FROM `user` WHERE deleted_at IS NULL" + countedUser(null),
                new MapSqlParameterSource());
    }

    public long countNotificationEnabledUsers() {
        return count("SELECT COUNT(*) FROM `user` WHERE deleted_at IS NULL AND notification_enabled = 1" + countedUser(null),
                new MapSqlParameterSource());
    }

    public long countUsersWithFcmToken() {
        return count("""
                SELECT COUNT(DISTINCT f.user_id) FROM fcm_token f
                JOIN `user` u ON u.id = f.user_id AND u.deleted_at IS NULL
                WHERE f.deleted_at IS NULL
                """ + countedUser("u"), new MapSqlParameterSource());
    }

    public long countSignups(LocalDate start, LocalDate end) {
        return countInRange("`user`", "created_at", start, end);
    }

    public List<LabelCount> signupsByPlatform(LocalDate start, LocalDate end) {
        return labelCounts("""
                SELECT UPPER(COALESCE(platform, '알 수 없음')) label, COUNT(*) c FROM `user`
                WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end
                """ + countedUser(null) + """
                GROUP BY UPPER(COALESCE(platform, '알 수 없음')) ORDER BY c DESC
                """, range(start, end));
    }

    /** 기간 안에 한 번이라도 로그인한 서로 다른 유저 수. 출석 미션은 하루 한 번만 남으므로 방문 횟수는 따로 세지 않는다. */
    public long countActiveUsers(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(DISTINCT user_id) FROM mission_log
                WHERE mission_type = :login AND deleted_at IS NULL AND created_at >= :start AND created_at < :end
                """ + excludeTestUsers("user_id"), range(start, end).addValue("login", USER_LOGIN));
    }

    /**
     * 기간에 가입한 유저 가운데 가입 N일 뒤 하루 동안 로그인한 비율.
     *
     * <p>N일째가 아직 끝나지 않은 가입자까지 넣으면 최근 가입자가 모두 이탈로 잡혀 비율이 낮아진다.
     * 그래서 {@code today - N} 전에 가입한 사람만 코호트로 본다.
     */
    public Retention retention(LocalDate start, LocalDate end, int dayOffset, LocalDate today) {
        LocalDate cohortEndExclusive = end.plusDays(1).isBefore(today.minusDays(dayOffset))
                ? end.plusDays(1)
                : today.minusDays(dayOffset);
        if (!cohortEndExclusive.isAfter(start)) {
            return new Retention(0, 0);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", start.atStartOfDay())
                .addValue("end", cohortEndExclusive.atStartOfDay())
                .addValue("login", USER_LOGIN)
                .addValue("offset", dayOffset);
        return jdbc.queryForObject("""
                SELECT COUNT(*) cohort,
                       COALESCE(SUM(EXISTS (
                           SELECT 1 FROM mission_log m
                           WHERE m.user_id = u.id AND m.mission_type = :login AND m.deleted_at IS NULL
                             AND m.created_at >= DATE(u.created_at) + INTERVAL :offset DAY
                             AND m.created_at < DATE(u.created_at) + INTERVAL (:offset + 1) DAY
                       )), 0) retained
                FROM `user` u
                WHERE u.deleted_at IS NULL AND u.created_at >= :start AND u.created_at < :end
                """ + countedUser("u"), params, (rs, i) -> new Retention(rs.getLong("cohort"), rs.getLong("retained")));
    }

    public List<LabelCount> levelDistribution() {
        return labelCounts("""
                SELECT CASE
                         WHEN COALESCE(total_study_level, 1) <= 1 THEN 'Lv.1'
                         WHEN total_study_level <= 3 THEN 'Lv.2~3'
                         WHEN total_study_level <= 5 THEN 'Lv.4~5'
                         WHEN total_study_level <= 9 THEN 'Lv.6~9'
                         ELSE 'Lv.10 이상'
                       END label,
                       COUNT(*) c
                FROM `user`
                WHERE deleted_at IS NULL
                """ + countedUser(null) + """
                GROUP BY label
                ORDER BY MIN(COALESCE(total_study_level, 1))
                """, new MapSqlParameterSource());
    }

    // ---------- 학습 ----------

    public long countAll(String table) {
        return count("SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL" + testUserFilter(table),
                new MapSqlParameterSource());
    }

    public long countProblems(LocalDate start, LocalDate end) {
        return countInRange("problem", "created_at", start, end);
    }

    public long countProblemWriters(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(DISTINCT user_id) FROM problem
                WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end
                """ + excludeTestUsers("user_id"), range(start, end));
    }

    public long countSolves(LocalDate start, LocalDate end) {
        return countInRange("problem_solve", "practiced_at", start, end);
    }

    public long countSolvers(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(DISTINCT user_id) FROM problem_solve
                WHERE deleted_at IS NULL AND practiced_at >= :start AND practiced_at < :end
                """ + excludeTestUsers("user_id"), range(start, end));
    }

    public Map<String, Long> solvesByAnswerStatus(LocalDate start, LocalDate end) {
        Map<String, Long> result = new LinkedHashMap<>();
        labelCounts("""
                SELECT COALESCE(answer_status, 'UNKNOWN') label, COUNT(*) c FROM problem_solve
                WHERE deleted_at IS NULL AND practiced_at >= :start AND practiced_at < :end
                """ + excludeTestUsers("user_id") + """
                GROUP BY COALESCE(answer_status, 'UNKNOWN')
                """, range(start, end)).forEach(row -> result.put(row.label(), row.count()));
        return result;
    }

    public Double averageSolveSeconds(LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT AVG(time_spent_seconds) FROM problem_solve
                WHERE deleted_at IS NULL AND time_spent_seconds IS NOT NULL AND time_spent_seconds > 0
                  AND practiced_at >= :start AND practiced_at < :end
                """ + excludeTestUsers("user_id"), range(start, end), Double.class);
    }

    public long countSolvesWithReflection(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(*) FROM problem_solve
                WHERE deleted_at IS NULL AND reflection IS NOT NULL AND TRIM(reflection) <> ''
                  AND practiced_at >= :start AND practiced_at < :end
                """ + excludeTestUsers("user_id"), range(start, end));
    }

    public long countSolveMoods(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(*) FROM problem_solve
                WHERE deleted_at IS NULL AND mood_emoji_key IS NOT NULL
                  AND practiced_at >= :start AND practiced_at < :end
                """ + excludeTestUsers("user_id"), range(start, end));
    }

    public long countPracticeNotes(LocalDate start, LocalDate end) {
        return countInRange("practice_note", "created_at", start, end);
    }

    /** 복습노트를 처음 끝낸 횟수. 같은 노트를 다시 끝내도 mission_log 는 한 번만 남는다. */
    public long countPracticeNoteFirstCompletions(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(*) FROM mission_log
                WHERE mission_type = :notePractice AND deleted_at IS NULL AND created_at >= :start AND created_at < :end
                """ + excludeTestUsers("user_id"), range(start, end).addValue("notePractice", NOTE_PRACTICE));
    }

    public long countInRange(String table, String column, LocalDate start, LocalDate end) {
        return count("SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL AND "
                + column + " >= :start AND " + column + " < :end" + testUserFilter(table), range(start, end));
    }

    // ---------- AI 분석 ----------

    public List<LabelCount> analysisStatuses() {
        return labelCounts("""
                SELECT COALESCE(pa.status, 'UNKNOWN') label, COUNT(*) c FROM problem_analysis pa
                JOIN problem p ON p.id = pa.problem_id AND p.deleted_at IS NULL
                WHERE pa.deleted_at IS NULL
                """ + excludeTestUsers("p.user_id") + """
                GROUP BY COALESCE(pa.status, 'UNKNOWN') ORDER BY c DESC
                """, new MapSqlParameterSource());
    }

    /** 기간은 문제가 등록된 시점 기준이다. 분석은 등록 직후에 돌기 때문에 등록일로 묶어야 날짜별 실패율이 맞는다. */
    public List<LabelCount> analysisStatuses(LocalDate start, LocalDate end) {
        return labelCounts("""
                SELECT COALESCE(pa.status, 'UNKNOWN') label, COUNT(*) c FROM problem_analysis pa
                JOIN problem p ON p.id = pa.problem_id AND p.deleted_at IS NULL
                WHERE pa.deleted_at IS NULL AND p.created_at >= :start AND p.created_at < :end
                """ + excludeTestUsers("p.user_id") + """
                GROUP BY COALESCE(pa.status, 'UNKNOWN') ORDER BY c DESC
                """, range(start, end));
    }

    public List<LabelCount> analysisSubjects(LocalDate start, LocalDate end, int limit) {
        return labelCounts("""
                SELECT pa.subject label, COUNT(*) c FROM problem_analysis pa
                JOIN problem p ON p.id = pa.problem_id AND p.deleted_at IS NULL
                WHERE pa.deleted_at IS NULL AND pa.subject IS NOT NULL AND TRIM(pa.subject) <> ''
                  AND p.created_at >= :start AND p.created_at < :end
                """ + excludeTestUsers("p.user_id") + """
                GROUP BY pa.subject ORDER BY c DESC LIMIT :limit
                """, range(start, end).addValue("limit", limit));
    }

    // ---------- 성장 ----------

    public long countMissionsClaimed(LocalDate start, LocalDate end) {
        return countInRange("mission_progress", "claimed_at", start, end);
    }

    public List<LabelCount> topCompletedMissions(LocalDate start, LocalDate end, int limit) {
        return labelCounts("""
                SELECT COALESCE(d.title, CONCAT('미션 #', mp.mission_id)) label, COUNT(*) c
                FROM mission_progress mp
                LEFT JOIN mission_definition d ON d.id = mp.mission_id
                WHERE mp.deleted_at IS NULL AND mp.completed_at >= :start AND mp.completed_at < :end
                """ + excludeTestUsers("mp.user_id") + """
                GROUP BY mp.mission_id, d.title ORDER BY c DESC LIMIT :limit
                """, range(start, end).addValue("limit", limit));
    }

    /** 업적 키별 획득 수. 화면 이름은 Achievement enum 에 있어서 컨트롤러가 바꿔 붙인다. */
    public List<LabelCount> achievementsEarned(LocalDate start, LocalDate end) {
        return labelCounts("""
                SELECT achievement_key label, COUNT(*) c FROM user_achievement
                WHERE earned_at >= :start AND earned_at < :end
                """ + excludeTestUsers("user_id") + """
                GROUP BY achievement_key ORDER BY c DESC
                """, range(start, end));
    }

    public long countCosmeticUsers() {
        return count("""
                SELECT COUNT(DISTINCT l.user_id) FROM user_cosmetic_loadout l
                JOIN `user` u ON u.id = l.user_id AND u.deleted_at IS NULL
                WHERE 1 = 1
                """ + countedUser("u"), new MapSqlParameterSource());
    }

    // ---------- 스터디룸 ----------

    public long countRoomReactions(LocalDate start, LocalDate end) {
        return countInRange("study_room_shared_problem_reaction", "created_at", start, end)
                + countInRange("study_room_shared_problem_comment_reaction", "created_at", start, end)
                + countInRange("study_room_feed_reaction", "created_at", start, end);
    }

    public long countChallengesCompleted(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(*) FROM study_room_challenge
                WHERE deleted_at IS NULL AND status = 'COMPLETED' AND completed_at >= :start AND completed_at < :end
                """ + excludeTestUsers("created_by_user_id"), range(start, end));
    }

    /** 실패한 챌린지에는 완료 시각이 없어서 마감 시각으로 기간을 가른다. */
    public long countChallengesFailed(LocalDate start, LocalDate end) {
        return count("""
                SELECT COUNT(*) FROM study_room_challenge
                WHERE deleted_at IS NULL AND status = 'FAILED' AND end_at >= :start AND end_at < :end
                """ + excludeTestUsers("created_by_user_id"), range(start, end));
    }

    // ---------- 참여 상위 유저 ----------

    public List<RankRow> topProblemWriters(LocalDate start, LocalDate end, int limit) {
        return topUsers("problem", "created_at", start, end, limit);
    }

    public List<RankRow> topSolvers(LocalDate start, LocalDate end, int limit) {
        return topUsers("problem_solve", "practiced_at", start, end, limit);
    }

    private List<RankRow> topUsers(String table, String column, LocalDate start, LocalDate end, int limit) {
        return jdbc.query("SELECT t.user_id, u.name, u.email, COUNT(*) c FROM " + table + " t "
                        + "LEFT JOIN `user` u ON u.id = t.user_id "
                        + "WHERE t.deleted_at IS NULL AND t." + column + " >= :start AND t." + column + " < :end "
                        + excludeTestUsers("t.user_id")
                        + "GROUP BY t.user_id, u.name, u.email ORDER BY c DESC LIMIT :limit",
                range(start, end).addValue("limit", limit),
                (rs, i) -> new RankRow(rs.getLong("user_id"), rs.getString("name"), rs.getString("email"), rs.getLong("c")));
    }

    // ---------- 홈 ----------

    public List<RecentUser> recentUsers(int limit) {
        return jdbc.query("""
                SELECT id, name, email, platform, created_at FROM `user`
                WHERE deleted_at IS NULL ORDER BY created_at DESC, id DESC LIMIT :limit
                """, new MapSqlParameterSource("limit", limit),
                (rs, i) -> new RecentUser(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("platform"), rs.getObject("created_at", LocalDateTime.class)));
    }

    public List<RecentProblem> recentProblems(int limit) {
        return jdbc.query("""
                SELECT p.id, p.user_id, u.name, p.reference, p.memo, pa.status, p.created_at
                FROM problem p
                LEFT JOIN `user` u ON u.id = p.user_id
                LEFT JOIN problem_analysis pa ON pa.problem_id = p.id AND pa.deleted_at IS NULL
                WHERE p.deleted_at IS NULL ORDER BY p.created_at DESC, p.id DESC LIMIT :limit
                """, new MapSqlParameterSource("limit", limit),
                (rs, i) -> new RecentProblem(rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"),
                        rs.getString("reference"), rs.getString("memo"), rs.getString("status"),
                        rs.getObject("created_at", LocalDateTime.class)));
    }

    public List<RecentSolve> recentSolves(int limit) {
        return jdbc.query("""
                SELECT s.id, s.problem_id, s.user_id, u.name, s.answer_status, s.practiced_at
                FROM problem_solve s
                LEFT JOIN `user` u ON u.id = s.user_id
                WHERE s.deleted_at IS NULL ORDER BY s.practiced_at DESC, s.id DESC LIMIT :limit
                """, new MapSqlParameterSource("limit", limit),
                (rs, i) -> new RecentSolve(rs.getLong("id"), rs.getLong("problem_id"), rs.getLong("user_id"),
                        rs.getString("name"), rs.getString("answer_status"),
                        rs.getObject("practiced_at", LocalDateTime.class)));
    }

    public List<RecentFeedback> recentFeedbacks(int limit) {
        return jdbc.query("""
                SELECT id, nps_score, most_used_feature, pain_points, submitted_at FROM user_feedback
                ORDER BY submitted_at DESC, id DESC LIMIT :limit
                """, new MapSqlParameterSource("limit", limit),
                (rs, i) -> new RecentFeedback(rs.getLong("id"), (Integer) rs.getObject("nps_score"),
                        rs.getString("most_used_feature"), rs.getString("pain_points"),
                        rs.getObject("submitted_at", LocalDateTime.class)));
    }

    // ---------- 공통 ----------

    private Map<LocalDate, Long> dailyCount(String table, String column, LocalDate start, LocalDate end) {
        return daily("SELECT DATE(" + column + ") d, COUNT(*) c FROM " + table
                        + " WHERE deleted_at IS NULL AND " + column + " >= :start AND " + column + " < :end"
                        + testUserFilter(table)
                        + " GROUP BY DATE(" + column + ")",
                start, end, range(start, end));
    }

    /** 테이블마다 누가 남긴 행인지 가리키는 컬럼이 달라서, 이름으로 골라 게스트와 관리자 조건을 붙인다. */
    private static String testUserFilter(String table) {
        return switch (table) {
            case "`user`" -> countedUser(null);
            case "problem", "problem_solve", "practice_note", "mission_progress", "mission_log", "folder", "tag",
                 "fcm_token", "learning_calendar_mood", "study_room_member", "study_room_feed",
                 "study_room_shared_problem_reaction", "study_room_shared_problem_comment_reaction",
                 "study_room_feed_reaction", "user_achievement" -> excludeTestUsers("user_id");
            case "study_room" -> excludeTestUsers("host_user_id");
            case "study_room_shared_problem" -> excludeTestUsers("shared_by_user_id");
            case "study_room_shared_problem_comment" -> excludeTestUsers("author_id");
            case "study_room_challenge" -> excludeTestUsers("created_by_user_id");
            default -> throw new IllegalArgumentException("유저 컬럼을 모르는 테이블: " + table);
        };
    }

    /** 기록이 없는 날도 0 으로 채운다. 날짜가 비면 차트 선이 끊기고 표에서 날짜가 빠진다. */
    private Map<LocalDate, Long> daily(String sql, LocalDate start, LocalDate end, MapSqlParameterSource params) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            result.put(d, 0L);
        }
        jdbc.query(sql, params, rs -> {
            LocalDate date = rs.getObject("d", LocalDate.class);
            if (date != null && result.containsKey(date)) {
                result.put(date, rs.getLong("c"));
            }
        });
        return result;
    }

    private List<LabelCount> labelCounts(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("c")));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    /** 끝 날짜를 포함하도록 다음 날 0시를 배타 상한으로 쓴다. */
    private MapSqlParameterSource range(LocalDate start, LocalDate end) {
        return new MapSqlParameterSource()
                .addValue("start", start.atStartOfDay())
                .addValue("end", end.plusDays(1).atStartOfDay());
    }
}
