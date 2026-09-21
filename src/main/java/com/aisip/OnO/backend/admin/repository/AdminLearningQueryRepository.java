package com.aisip.OnO.backend.admin.repository;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ImageRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteRef;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.NoteSummary;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemDetail;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemSummary;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SharedRoomRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveRow;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.SolveSummary;
import com.aisip.OnO.backend.admin.dto.AdminLearningDto.TagRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 학습 기록 화면 전용 조회.
 *
 * <p>목록마다 작성자, 폴더, 분석 상태, 복습 횟수를 같이 보여 줘야 하는데 엔티티를 따라가면
 * 행마다 쿼리가 나간다. 한 번의 SQL 로 필요한 열만 가져오고, 행별 개수는 페이지 크기만큼만
 * 도는 상관 서브쿼리로 센다(problem_solve, problem_tag_mapping 모두 problem_id 인덱스가 있다).
 *
 * <p>소프트 삭제된 행은 제외한다. 작성자는 탈퇴했어도 누구의 기록인지 알아야 해서 user 조인에는
 * deleted_at 조건을 걸지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminLearningQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 목록 필터. 값이 null 이면 그 조건은 걸지 않는다. */
    public record Filter(LocalDate date, Long userId, String status) {
        public static Filter none() {
            return new Filter(null, null, null);
        }
    }

    // ---------- 오답노트 ----------

    private static final String PROBLEM_FROM = """
            FROM problem p
            LEFT JOIN `user` u ON u.id = p.user_id
            LEFT JOIN folder f ON f.id = p.folder_id
            LEFT JOIN problem_analysis pa ON pa.problem_id = p.id AND pa.deleted_at IS NULL
            WHERE p.deleted_at IS NULL
            """;

    public long countProblems(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = problemWhere(filter, params);
        return queryLong("SELECT COUNT(*) " + PROBLEM_FROM + where, params);
    }

    public List<ProblemRow> findProblems(Filter filter, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String where = problemWhere(filter, params);
        String sql = """
                SELECT p.id, p.user_id, u.name AS user_name, u.email AS user_email,
                       p.folder_id, f.name AS folder_name, p.reference, p.memo,
                       pa.status, pa.subject, pa.problem_type, p.created_at,
                       (SELECT COUNT(*) FROM problem_solve s
                         WHERE s.problem_id = p.id AND s.deleted_at IS NULL) AS solve_count,
                       (SELECT COUNT(*) FROM problem_tag_mapping t
                         WHERE t.problem_id = p.id AND t.deleted_at IS NULL) AS tag_count
                """ + PROBLEM_FROM + where + " ORDER BY p.id DESC LIMIT :limit OFFSET :offset";

        return jdbc.query(sql, params, (rs, i) -> new ProblemRow(
                rs.getLong("id"),
                nullableLong(rs, "user_id"),
                rs.getString("user_name"),
                rs.getString("user_email"),
                nullableLong(rs, "folder_id"),
                rs.getString("folder_name"),
                rs.getString("reference"),
                rs.getString("memo"),
                rs.getString("status"),
                rs.getString("subject"),
                rs.getString("problem_type"),
                rs.getLong("solve_count"),
                rs.getLong("tag_count"),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    private String problemWhere(Filter filter, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (filter.date() != null) {
            where.append(" AND p.created_at >= :from AND p.created_at < :to");
            params.addValue("from", filter.date().atStartOfDay()).addValue("to", filter.date().plusDays(1).atStartOfDay());
        }
        if (filter.userId() != null) {
            where.append(" AND p.user_id = :userId");
            params.addValue("userId", filter.userId());
        }
        if (filter.status() != null) {
            where.append(" AND pa.status = :status");
            params.addValue("status", filter.status());
        }
        return where.toString();
    }

    public ProblemSummary summarizeProblems(LocalDate today) {
        String sql = """
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(p.created_at >= :todayStart), 0) AS today,
                       COALESCE(SUM(pa.status = 'FAILED'), 0) AS failed,
                       COALESCE(SUM(pa.status = 'RATE_LIMIT_EXCEEDED'), 0) AS rate_limited,
                       COALESCE(SUM(pa.status = 'PROCESSING'), 0) AS processing
                FROM problem p
                LEFT JOIN problem_analysis pa ON pa.problem_id = p.id AND pa.deleted_at IS NULL
                WHERE p.deleted_at IS NULL
                """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("todayStart", today.atStartOfDay()),
                (rs, i) -> new ProblemSummary(
                        rs.getLong("total"),
                        rs.getLong("today"),
                        rs.getLong("failed"),
                        rs.getLong("rate_limited"),
                        rs.getLong("processing")
                ));
    }

    public Optional<ProblemDetail> findProblemDetail(Long problemId) {
        String sql = """
                SELECT p.id, p.user_id, u.name AS user_name, u.email AS user_email,
                       (u.id IS NULL OR u.deleted_at IS NOT NULL) AS user_deleted,
                       p.folder_id, f.name AS folder_name, p.reference, p.memo, p.created_at, p.solved_at,
                       p.next_review_at, p.review_interval, p.consecutive_correct_count,
                       pa.status, pa.subject, pa.problem_type, pa.key_points, pa.solution,
                       pa.common_mistakes, pa.study_tips, pa.error_message
                FROM problem p
                LEFT JOIN `user` u ON u.id = p.user_id
                LEFT JOIN folder f ON f.id = p.folder_id
                LEFT JOIN problem_analysis pa ON pa.problem_id = p.id AND pa.deleted_at IS NULL
                WHERE p.id = :problemId AND p.deleted_at IS NULL
                """;
        List<ProblemDetail> rows = jdbc.query(sql, new MapSqlParameterSource("problemId", problemId), (rs, i) -> {
            java.sql.Date nextReviewAt = rs.getDate("next_review_at");
            return new ProblemDetail(
                    rs.getLong("id"),
                    nullableLong(rs, "user_id"),
                    rs.getString("user_name"),
                    rs.getString("user_email"),
                    rs.getBoolean("user_deleted"),
                    nullableLong(rs, "folder_id"),
                    rs.getString("folder_name"),
                    rs.getString("reference"),
                    rs.getString("memo"),
                    toDateTime(rs.getTimestamp("created_at")),
                    toDateTime(rs.getTimestamp("solved_at")),
                    nextReviewAt == null ? null : nextReviewAt.toLocalDate(),
                    nullableInt(rs, "review_interval"),
                    nullableInt(rs, "consecutive_correct_count"),
                    rs.getString("status"),
                    rs.getString("subject"),
                    rs.getString("problem_type"),
                    rs.getString("key_points"),
                    rs.getString("solution"),
                    rs.getString("common_mistakes"),
                    rs.getString("study_tips"),
                    rs.getString("error_message")
            );
        });
        return rows.stream().findFirst();
    }

    public List<ImageRow> findProblemImages(Long problemId) {
        String sql = """
                SELECT image_url, image_type FROM image_data
                WHERE problem_id = :problemId AND deleted_at IS NULL
                ORDER BY image_type, id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("problemId", problemId),
                (rs, i) -> new ImageRow(rs.getString("image_url"), rs.getString("image_type")));
    }

    public List<TagRow> findProblemTags(Long problemId) {
        String sql = """
                SELECT t.id, t.name FROM problem_tag_mapping m
                JOIN tag t ON t.id = m.tag_id AND t.deleted_at IS NULL
                WHERE m.problem_id = :problemId AND m.deleted_at IS NULL
                ORDER BY t.name
                """;
        return jdbc.query(sql, new MapSqlParameterSource("problemId", problemId),
                (rs, i) -> new TagRow(rs.getLong("id"), rs.getString("name")));
    }

    public List<NoteRef> findNotesContainingProblem(Long problemId) {
        String sql = """
                SELECT n.id, n.title, n.practice_count, n.last_solved_at
                FROM problem_practice_note_mapping m
                JOIN practice_note n ON n.id = m.practice_note_id AND n.deleted_at IS NULL
                WHERE m.problem_id = :problemId AND m.deleted_at IS NULL
                ORDER BY n.id DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("problemId", problemId), (rs, i) -> new NoteRef(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getLong("practice_count"),
                toDateTime(rs.getTimestamp("last_solved_at"))
        ));
    }

    public List<SharedRoomRow> findRoomsSharingProblem(Long problemId) {
        String sql = """
                SELECT sp.id, sp.room_id, r.name AS room_name, sp.shared_by_user_id, u.name AS shared_by_name,
                       sp.comment, sp.created_at,
                       (SELECT COUNT(*) FROM study_room_shared_problem_comment c
                         WHERE c.shared_problem_id = sp.id AND c.deleted_at IS NULL) AS comment_count,
                       (SELECT COUNT(*) FROM study_room_shared_problem_reaction x
                         WHERE x.shared_problem_id = sp.id AND x.deleted_at IS NULL) AS reaction_count
                FROM study_room_shared_problem sp
                LEFT JOIN study_room r ON r.id = sp.room_id
                LEFT JOIN `user` u ON u.id = sp.shared_by_user_id
                WHERE sp.problem_id = :problemId AND sp.deleted_at IS NULL
                ORDER BY sp.id DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("problemId", problemId), (rs, i) -> new SharedRoomRow(
                rs.getLong("id"),
                nullableLong(rs, "room_id"),
                rs.getString("room_name"),
                nullableLong(rs, "shared_by_user_id"),
                rs.getString("shared_by_name"),
                rs.getString("comment"),
                rs.getLong("comment_count"),
                rs.getLong("reaction_count"),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    // ---------- 복습 기록 (problem_solve) ----------

    private static final String SOLVE_SELECT = """
            SELECT s.id, s.practiced_at, s.user_id, u.name AS user_name, u.email AS user_email,
                   s.problem_id, p.reference, p.memo, s.answer_status, s.time_spent_seconds,
                   s.reflection, s.improvements, s.mood_emoji_key, s.migrated_from_legacy,
                   (SELECT COUNT(*) FROM problem_solve_image_data i
                     WHERE i.problem_solve_id = s.id AND i.deleted_at IS NULL) AS image_count
            FROM problem_solve s
            LEFT JOIN `user` u ON u.id = s.user_id
            LEFT JOIN problem p ON p.id = s.problem_id
            """;

    private static final RowMapper<SolveRow> SOLVE_ROW = (rs, i) -> new SolveRow(
            rs.getLong("id"),
            toDateTime(rs.getTimestamp("practiced_at")),
            nullableLong(rs, "user_id"),
            rs.getString("user_name"),
            rs.getString("user_email"),
            nullableLong(rs, "problem_id"),
            rs.getString("reference"),
            rs.getString("memo"),
            rs.getString("answer_status"),
            nullableInt(rs, "time_spent_seconds"),
            rs.getString("reflection"),
            rs.getString("improvements"),
            rs.getString("mood_emoji_key"),
            rs.getBoolean("migrated_from_legacy"),
            rs.getLong("image_count"),
            List.of()
    );

    public long countSolves(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return queryLong("SELECT COUNT(*) FROM problem_solve s WHERE s.deleted_at IS NULL" + solveWhere(filter, params), params);
    }

    public List<SolveRow> findSolves(Filter filter, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String sql = SOLVE_SELECT + " WHERE s.deleted_at IS NULL" + solveWhere(filter, params)
                + " ORDER BY s.practiced_at DESC, s.id DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, SOLVE_ROW);
    }

    public SolveSummary summarizeSolves(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = """
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(s.answer_status = 'CORRECT'), 0) AS correct,
                       COALESCE(SUM(s.answer_status = 'WRONG'), 0) AS wrong,
                       COALESCE(SUM(s.answer_status = 'PARTIAL'), 0) AS partial,
                       COALESCE(SUM(s.answer_status = 'UNKNOWN'), 0) AS unknown,
                       AVG(s.time_spent_seconds) AS avg_time,
                       COALESCE(SUM(s.reflection IS NOT NULL AND s.reflection <> ''), 0) AS with_reflection
                FROM problem_solve s
                WHERE s.deleted_at IS NULL
                """ + solveWhere(filter, params);
        return jdbc.queryForObject(sql, params, (rs, i) -> {
            double avg = rs.getDouble("avg_time");
            Double averageTime = rs.wasNull() ? null : avg;
            return new SolveSummary(
                    rs.getLong("total"),
                    rs.getLong("correct"),
                    rs.getLong("wrong"),
                    rs.getLong("partial"),
                    rs.getLong("unknown"),
                    averageTime,
                    rs.getLong("with_reflection")
            );
        });
    }

    public long countSolvesOn(LocalDate date) {
        return countSolves(new Filter(date, null, null));
    }

    private String solveWhere(Filter filter, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (filter.date() != null) {
            where.append(" AND s.practiced_at >= :from AND s.practiced_at < :to");
            params.addValue("from", filter.date().atStartOfDay()).addValue("to", filter.date().plusDays(1).atStartOfDay());
        }
        if (filter.userId() != null) {
            where.append(" AND s.user_id = :userId");
            params.addValue("userId", filter.userId());
        }
        if (filter.status() != null) {
            where.append(" AND s.answer_status = :status");
            params.addValue("status", filter.status());
        }
        return where.toString();
    }

    /** 한 문제의 복습 기록 전체. 이미지까지 붙여서 돌려준다. */
    public List<SolveRow> findSolvesOfProblem(Long problemId) {
        String sql = SOLVE_SELECT + """
                 WHERE s.problem_id = :problemId AND s.deleted_at IS NULL
                 ORDER BY s.practiced_at DESC, s.id DESC
                """;
        return attachImages(jdbc.query(sql, new MapSqlParameterSource("problemId", problemId), SOLVE_ROW));
    }

    /** 복습노트 상세에서 보여 줄, 노트 주인이 노트 속 문제들에 남긴 최근 복습 기록. */
    public List<SolveRow> findRecentSolvesOfProblems(Long userId, Collection<Long> problemIds, int limit) {
        if (problemIds.isEmpty()) {
            return List.of();
        }
        String sql = SOLVE_SELECT + """
                 WHERE s.problem_id IN (:problemIds) AND s.user_id = :userId AND s.deleted_at IS NULL
                 ORDER BY s.practiced_at DESC, s.id DESC
                 LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("problemIds", problemIds)
                .addValue("userId", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, SOLVE_ROW);
    }

    private List<SolveRow> attachImages(List<SolveRow> solves) {
        if (solves.isEmpty()) {
            return solves;
        }
        List<Long> ids = solves.stream().map(SolveRow::solveId).toList();
        Map<Long, List<String>> imagesBySolve = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT problem_solve_id, image_url FROM problem_solve_image_data
                        WHERE problem_solve_id IN (:ids) AND deleted_at IS NULL
                        ORDER BY problem_solve_id, image_order, id
                        """,
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    imagesBySolve.computeIfAbsent(rs.getLong("problem_solve_id"), k -> new java.util.ArrayList<>())
                            .add(rs.getString("image_url"));
                });
        return solves.stream()
                .map(s -> s.withImages(imagesBySolve.getOrDefault(s.solveId(), List.of())))
                .toList();
    }

    // ---------- 복습노트 ----------

    private static final String NOTE_SELECT = """
            SELECT n.id, n.title, n.user_id, u.name AS user_name, u.email AS user_email,
                   n.practice_count, n.last_solved_at, n.last_session_mood_emoji_key,
                   n.repeat_type, n.interval_days, n.hour, n.minute, n.created_at,
                   (SELECT COUNT(*) FROM problem_practice_note_mapping m
                     WHERE m.practice_note_id = n.id AND m.deleted_at IS NULL) AS problem_count
            FROM practice_note n
            LEFT JOIN `user` u ON u.id = n.user_id
            """;

    // week_days 는 JPA 가 List 를 직렬화한 varbinary 라 SQL 로는 읽지 않는다. 요일은 호출하는 쪽에서
    // 엔티티로 한 번에 읽어 withWeekDays 로 붙인다.
    private static final RowMapper<NoteRow> NOTE_ROW = (rs, i) -> new NoteRow(
            rs.getLong("id"),
            rs.getString("title"),
            nullableLong(rs, "user_id"),
            rs.getString("user_name"),
            rs.getString("user_email"),
            rs.getLong("problem_count"),
            rs.getLong("practice_count"),
            toDateTime(rs.getTimestamp("last_solved_at")),
            rs.getString("last_session_mood_emoji_key"),
            rs.getString("repeat_type"),
            nullableInt(rs, "interval_days"),
            nullableInt(rs, "hour"),
            nullableInt(rs, "minute"),
            List.of(),
            toDateTime(rs.getTimestamp("created_at"))
    );

    public long countNotes(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return queryLong("SELECT COUNT(*) FROM practice_note n WHERE n.deleted_at IS NULL" + noteWhere(filter, params), params);
    }

    public List<NoteRow> findNotes(Filter filter, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String sql = NOTE_SELECT + " WHERE n.deleted_at IS NULL" + noteWhere(filter, params)
                + " ORDER BY n.id DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, NOTE_ROW);
    }

    public Optional<NoteRow> findNote(Long noteId) {
        String sql = NOTE_SELECT + " WHERE n.id = :noteId AND n.deleted_at IS NULL";
        return jdbc.query(sql, new MapSqlParameterSource("noteId", noteId), NOTE_ROW).stream().findFirst();
    }

    public NoteSummary summarizeNotes() {
        String sql = """
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(n.hour IS NOT NULL AND n.minute IS NOT NULL), 0) AS with_notification,
                       COALESCE(SUM(n.practice_count > 0), 0) AS practiced,
                       COALESCE(SUM(n.practice_count), 0) AS total_practice_count
                FROM practice_note n
                WHERE n.deleted_at IS NULL
                """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, i) -> new NoteSummary(
                rs.getLong("total"),
                rs.getLong("with_notification"),
                rs.getLong("practiced"),
                rs.getLong("total_practice_count")
        ));
    }

    private String noteWhere(Filter filter, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (filter.date() != null) {
            where.append(" AND n.created_at >= :from AND n.created_at < :to");
            params.addValue("from", filter.date().atStartOfDay()).addValue("to", filter.date().plusDays(1).atStartOfDay());
        }
        if (filter.userId() != null) {
            where.append(" AND n.user_id = :userId");
            params.addValue("userId", filter.userId());
        }
        return where.toString();
    }

    public List<NoteProblemRow> findNoteProblems(Long noteId) {
        String sql = """
                SELECT p.id, p.reference, p.memo, p.created_at, pa.subject,
                       (SELECT COUNT(*) FROM problem_solve s
                         WHERE s.problem_id = p.id AND s.deleted_at IS NULL) AS solve_count,
                       (SELECT s.answer_status FROM problem_solve s
                         WHERE s.problem_id = p.id AND s.deleted_at IS NULL
                         ORDER BY s.practiced_at DESC, s.id DESC LIMIT 1) AS last_status,
                       (SELECT MAX(s.practiced_at) FROM problem_solve s
                         WHERE s.problem_id = p.id AND s.deleted_at IS NULL) AS last_practiced_at
                FROM problem_practice_note_mapping m
                JOIN problem p ON p.id = m.problem_id AND p.deleted_at IS NULL
                LEFT JOIN problem_analysis pa ON pa.problem_id = p.id AND pa.deleted_at IS NULL
                WHERE m.practice_note_id = :noteId AND m.deleted_at IS NULL
                ORDER BY m.id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("noteId", noteId), (rs, i) -> new NoteProblemRow(
                rs.getLong("id"),
                rs.getString("reference"),
                rs.getString("memo"),
                rs.getString("subject"),
                rs.getLong("solve_count"),
                rs.getString("last_status"),
                toDateTime(rs.getTimestamp("last_practiced_at")),
                toDateTime(rs.getTimestamp("created_at"))
        ));
    }

    // ---------- 공통 ----------

    /** 필터 칩에 보여 줄 유저 이름. 없는 유저면 null. */
    public String findUserName(Long userId) {
        List<String> names = jdbc.queryForList("SELECT name FROM `user` WHERE id = :userId",
                new MapSqlParameterSource("userId", userId), String.class);
        return names.isEmpty() ? null : names.get(0);
    }

    private long queryLong(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
