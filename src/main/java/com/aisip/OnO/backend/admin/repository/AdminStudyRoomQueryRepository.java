package com.aisip.OnO.backend.admin.repository;

import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Challenge;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Comment;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Feed;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.InviteCode;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Member;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Overview;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomHeader;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.RoomRow;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.SharedProblem;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.WeeklyReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


import static com.aisip.OnO.backend.admin.repository.AdminSqlFilters.excludeTestUsers;
/**
 * 관리자 스터디룸 화면 전용 조회.
 *
 * <p>화면 하나가 방, 멤버, 챌린지, 공유 문제, 댓글, 피드, 주간 리포트를 한꺼번에 보여 줘서
 * 엔티티를 따라 지연 로딩하면 쿼리가 행 수만큼 늘어난다. 그래서 필요한 칸만 SQL 로 한 번에 모아 읽는다.
 * 읽기만 하고, 도메인 서비스의 규칙(권한 확인, 탈퇴 처리)은 거치지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminStudyRoomQueryRepository {

    private static final int FEED_LIMIT = 100;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** 목록 위 요약 카드. 게스트와 관리자 계정이 만들거나 남긴 것은 세지 않는다. */
    public Overview overview(LocalDateTime weekStart) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM study_room r WHERE r.deleted_at IS NULL
                   """ + excludeTestUsers("r.host_user_id") + """
                  ) AS total_rooms,
                  (SELECT COUNT(DISTINCT f.room_id) FROM study_room_feed f
                     JOIN study_room r ON r.id = f.room_id AND r.deleted_at IS NULL
                    WHERE f.deleted_at IS NULL AND f.created_at >= :weekStart
                   """ + excludeTestUsers("f.user_id") + """
                  ) AS active_rooms,
                  (SELECT COUNT(*) FROM study_room_member m
                     JOIN study_room r ON r.id = m.room_id AND r.deleted_at IS NULL
                    WHERE m.deleted_at IS NULL
                   """ + excludeTestUsers("m.user_id") + """
                  ) AS total_members,
                  (SELECT COUNT(*) FROM study_room_shared_problem sp
                     JOIN study_room r ON r.id = sp.room_id AND r.deleted_at IS NULL
                    WHERE sp.deleted_at IS NULL
                   """ + excludeTestUsers("sp.shared_by_user_id") + """
                  ) AS shared_problems,
                  (SELECT COUNT(*) FROM study_room_challenge c
                     JOIN study_room r ON r.id = c.room_id AND r.deleted_at IS NULL
                    WHERE c.deleted_at IS NULL AND c.status = 'IN_PROGRESS'
                   """ + excludeTestUsers("c.created_by_user_id") + """
                  ) AS in_progress_challenges
                """, new MapSqlParameterSource("weekStart", weekStart), (rs, i) -> new Overview(
                rs.getLong("total_rooms"),
                rs.getLong("active_rooms"),
                rs.getLong("total_members"),
                rs.getLong("shared_problems"),
                rs.getLong("in_progress_challenges")
        ));
    }

    public long countRooms() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_room WHERE deleted_at IS NULL",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 방 목록 한 페이지. 집계 칸은 방마다 상관 서브쿼리로 센다.
     * 한 페이지가 많아야 수십 개라 방 전체를 GROUP BY 하는 것보다 싸다.
     */
    public List<RoomRow> findRooms(int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT r.id, r.name, r.thumbnail_url, r.host_user_id, u.name AS host_name, r.created_at,
                  (SELECT COUNT(*) FROM study_room_member m
                    WHERE m.room_id = r.id AND m.deleted_at IS NULL) AS member_count,
                  (SELECT COUNT(*) FROM study_room_shared_problem sp
                    WHERE sp.room_id = r.id AND sp.deleted_at IS NULL) AS shared_count,
                  (SELECT COUNT(*) FROM study_room_shared_problem_comment c
                     JOIN study_room_shared_problem sp ON sp.id = c.shared_problem_id
                    WHERE sp.room_id = r.id AND sp.deleted_at IS NULL AND c.deleted_at IS NULL) AS comment_count,
                  (SELECT COUNT(*) FROM study_room_challenge ch
                    WHERE ch.room_id = r.id AND ch.deleted_at IS NULL AND ch.status = 'IN_PROGRESS') AS in_progress_count,
                  (SELECT COUNT(*) FROM study_room_challenge ch
                    WHERE ch.room_id = r.id AND ch.deleted_at IS NULL AND ch.status = 'COMPLETED') AS completed_count,
                  (SELECT MAX(f.created_at) FROM study_room_feed f
                    WHERE f.room_id = r.id AND f.deleted_at IS NULL) AS last_activity_at
                FROM study_room r
                LEFT JOIN user u ON u.id = r.host_user_id
                WHERE r.deleted_at IS NULL
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> new RoomRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("thumbnail_url"),
                nullableLong(rs, "host_user_id"),
                rs.getString("host_name"),
                rs.getLong("member_count"),
                rs.getLong("shared_count"),
                rs.getLong("comment_count"),
                rs.getLong("in_progress_count"),
                rs.getLong("completed_count"),
                rs.getObject("last_activity_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)
        ));
    }

    public Optional<RoomHeader> findRoom(Long roomId) {
        return jdbc.query("""
                SELECT r.id, r.name, r.thumbnail_url, r.host_user_id, u.name AS host_name, r.created_at
                FROM study_room r
                LEFT JOIN user u ON u.id = r.host_user_id
                WHERE r.id = :roomId AND r.deleted_at IS NULL
                """, new MapSqlParameterSource("roomId", roomId), (rs, i) -> new RoomHeader(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("thumbnail_url"),
                nullableLong(rs, "host_user_id"),
                rs.getString("host_name"),
                rs.getObject("created_at", LocalDateTime.class)
        )).stream().findFirst();
    }

    public List<InviteCode> findInviteCodes(Long roomId, LocalDateTime now) {
        return jdbc.query("""
                SELECT code, created_at, expired_at
                FROM study_room_invite_code
                WHERE room_id = :roomId AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT 5
                """, new MapSqlParameterSource("roomId", roomId), (rs, i) -> {
            LocalDateTime expiredAt = rs.getObject("expired_at", LocalDateTime.class);
            return new InviteCode(
                    rs.getString("code"),
                    rs.getObject("created_at", LocalDateTime.class),
                    expiredAt,
                    expiredAt != null && expiredAt.isBefore(now));
        });
    }

    public List<Member> findMembers(Long roomId) {
        return jdbc.query("""
                SELECT m.user_id, u.name, u.email, m.role, m.weekly_goal, m.created_at,
                  (SELECT COUNT(*) FROM study_room_shared_problem sp
                    WHERE sp.room_id = m.room_id AND sp.shared_by_user_id = m.user_id
                      AND sp.deleted_at IS NULL) AS shared_count
                FROM study_room_member m
                LEFT JOIN user u ON u.id = m.user_id
                WHERE m.room_id = :roomId AND m.deleted_at IS NULL
                ORDER BY (m.role = 'HOST') DESC, m.created_at ASC
                """, new MapSqlParameterSource("roomId", roomId), (rs, i) -> new Member(
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                "HOST".equals(rs.getString("role")),
                nullableInt(rs, "weekly_goal"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getLong("shared_count")
        ));
    }

    public List<Challenge> findChallenges(Long roomId) {
        return jdbc.query("""
                SELECT c.id, c.title, c.type, c.metric, c.period, c.period_days, c.target_value, c.status,
                       c.start_at, c.end_at, c.completed_at, u.name AS created_by_name
                FROM study_room_challenge c
                LEFT JOIN user u ON u.id = c.created_by_user_id
                WHERE c.room_id = :roomId AND c.deleted_at IS NULL
                ORDER BY (c.status = 'IN_PROGRESS') DESC, c.end_at DESC, c.id DESC
                """, new MapSqlParameterSource("roomId", roomId), (rs, i) -> {
            String status = rs.getString("status");
            return new Challenge(
                    rs.getLong("id"),
                    rs.getString("title"),
                    AdminStudyRoomLabels.challengeType(rs.getString("type")),
                    AdminStudyRoomLabels.challengeMetric(rs.getString("metric")),
                    AdminStudyRoomLabels.challengePeriod(rs.getString("period"), nullableInt(rs, "period_days")),
                    nullableInt(rs, "target_value"),
                    status,
                    AdminStudyRoomLabels.challengeStatus(status),
                    rs.getObject("start_at", LocalDateTime.class),
                    rs.getObject("end_at", LocalDateTime.class),
                    rs.getObject("completed_at", LocalDateTime.class),
                    rs.getString("created_by_name"));
        });
    }

    /** 공유 문제와 거기 달린 댓글. 댓글은 한 번에 읽어서 문제별로 나눠 붙인다. */
    public List<SharedProblem> findSharedProblems(Long roomId) {
        MapSqlParameterSource params = new MapSqlParameterSource("roomId", roomId);
        List<SharedProblem> sharedProblems = jdbc.query("""
                SELECT sp.id, sp.problem_id, sp.shared_by_user_id, u.name AS shared_by_name, sp.comment, sp.created_at,
                  (SELECT COUNT(*) FROM study_room_shared_problem_reaction re
                    WHERE re.shared_problem_id = sp.id AND re.deleted_at IS NULL) AS reaction_count,
                  (SELECT COUNT(*) FROM study_room_shared_problem_comment c
                    WHERE c.shared_problem_id = sp.id AND c.deleted_at IS NULL) AS comment_count
                FROM study_room_shared_problem sp
                LEFT JOIN user u ON u.id = sp.shared_by_user_id
                WHERE sp.room_id = :roomId AND sp.deleted_at IS NULL
                ORDER BY sp.created_at DESC, sp.id DESC
                """, params, (rs, i) -> new SharedProblem(
                rs.getLong("id"),
                nullableLong(rs, "problem_id"),
                nullableLong(rs, "shared_by_user_id"),
                rs.getString("shared_by_name"),
                rs.getString("comment"),
                rs.getLong("reaction_count"),
                rs.getLong("comment_count"),
                rs.getObject("created_at", LocalDateTime.class),
                List.of()
        ));
        if (sharedProblems.isEmpty()) {
            return sharedProblems;
        }

        Map<Long, List<Comment>> commentsBySharedProblem = jdbc.query("""
                SELECT c.shared_problem_id, c.author_id, u.name AS author_name, c.content, c.created_at,
                  (SELECT COUNT(*) FROM study_room_shared_problem_comment_reaction re
                    WHERE re.comment_id = c.id AND re.deleted_at IS NULL) AS reaction_count
                FROM study_room_shared_problem_comment c
                JOIN study_room_shared_problem sp ON sp.id = c.shared_problem_id
                LEFT JOIN user u ON u.id = c.author_id
                WHERE sp.room_id = :roomId AND sp.deleted_at IS NULL AND c.deleted_at IS NULL
                ORDER BY c.created_at ASC, c.id ASC
                """, params, (rs, i) -> new Comment(
                rs.getLong("shared_problem_id"),
                nullableLong(rs, "author_id"),
                rs.getString("author_name"),
                rs.getString("content"),
                rs.getLong("reaction_count"),
                rs.getObject("created_at", LocalDateTime.class)
        )).stream().collect(Collectors.groupingBy(Comment::sharedProblemId));

        return sharedProblems.stream()
                .map(sp -> sp.withComments(commentsBySharedProblem.getOrDefault(sp.id(), List.of())))
                .toList();
    }

    public List<Feed> findRecentFeeds(Long roomId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("limit", FEED_LIMIT);
        return jdbc.query("""
                SELECT f.id, f.event_type, f.metadata_json, f.user_id, u.name AS user_name, f.created_at,
                  (SELECT COUNT(*) FROM study_room_feed_reaction re
                    WHERE re.feed_id = f.id AND re.deleted_at IS NULL) AS reaction_count
                FROM study_room_feed f
                LEFT JOIN user u ON u.id = f.user_id
                WHERE f.room_id = :roomId AND f.deleted_at IS NULL
                ORDER BY f.created_at DESC, f.id DESC
                LIMIT :limit
                """, params, (rs, i) -> {
            String eventType = rs.getString("event_type");
            return new Feed(
                    rs.getLong("id"),
                    AdminStudyRoomLabels.feedEvent(eventType),
                    nullableLong(rs, "user_id"),
                    rs.getString("user_name"),
                    AdminStudyRoomLabels.feedSummary(eventType, readMetadata(rs.getString("metadata_json"))),
                    rs.getLong("reaction_count"),
                    rs.getObject("created_at", LocalDateTime.class));
        });
    }

    public List<WeeklyReport> findWeeklyReports(Long roomId) {
        return jdbc.query("""
                SELECT w.week_start, w.week_end, w.top_member_name, w.top_member_problem_count,
                       w.longest_streak_name, w.longest_streak_days, w.total_problems,
                       w.challenges_completed, w.cheer_message,
                  (SELECT COUNT(*) FROM study_room_weekly_report_read rr
                    WHERE rr.report_id = w.id AND rr.deleted_at IS NULL) AS read_count
                FROM study_room_weekly_report w
                WHERE w.room_id = :roomId AND w.deleted_at IS NULL
                ORDER BY w.week_start DESC
                LIMIT 12
                """, new MapSqlParameterSource("roomId", roomId), (rs, i) -> new WeeklyReport(
                rs.getObject("week_start", LocalDate.class),
                rs.getObject("week_end", LocalDate.class),
                rs.getString("top_member_name"),
                nullableInt(rs, "top_member_problem_count"),
                rs.getString("longest_streak_name"),
                nullableInt(rs, "longest_streak_days"),
                nullableInt(rs, "total_problems"),
                nullableInt(rs, "challenges_completed"),
                rs.getString("cheer_message"),
                rs.getLong("read_count")
        ));
    }

    /** 방 안의 반응을 공유 문제, 댓글, 피드 가리지 않고 모두 센다. */
    public long countReactions(Long roomId) {
        Long count = jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM study_room_shared_problem_reaction re
                     JOIN study_room_shared_problem sp ON sp.id = re.shared_problem_id
                    WHERE sp.room_id = :roomId AND sp.deleted_at IS NULL AND re.deleted_at IS NULL)
                + (SELECT COUNT(*) FROM study_room_shared_problem_comment_reaction re
                     JOIN study_room_shared_problem_comment c ON c.id = re.comment_id
                     JOIN study_room_shared_problem sp ON sp.id = c.shared_problem_id
                    WHERE sp.room_id = :roomId AND sp.deleted_at IS NULL
                      AND c.deleted_at IS NULL AND re.deleted_at IS NULL)
                + (SELECT COUNT(*) FROM study_room_feed_reaction re
                     JOIN study_room_feed f ON f.id = re.feed_id
                    WHERE f.room_id = :roomId AND f.deleted_at IS NULL AND re.deleted_at IS NULL)
                """, new MapSqlParameterSource("roomId", roomId), Long.class);
        return count == null ? 0 : count;
    }

    /** 메타데이터가 깨져 있어도 피드 한 줄 때문에 상세 화면 전체가 안 열리면 안 된다. */
    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
