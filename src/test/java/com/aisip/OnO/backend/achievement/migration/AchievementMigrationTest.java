package com.aisip.OnO.backend.achievement.migration;

import com.aisip.OnO.backend.achievement.support.AchievementTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션이 중간에 끊겨도 다시 돌릴 수 있는지, 그리고 엔티티와 스키마가 어긋나지 않는지.
 *
 * <p>테스트는 Flyway 를 끄고 Hibernate 가 만든 스키마를 쓴다({@code application-test.yml}).
 * 그래서 V42 가 실제로 적용 가능한지는 dev 서버 기동 전에는 드러나지 않는다. 최소한 재실행
 * 안전성과 엔티티-스키마 일치만이라도 여기서 잠근다. {@code CosmeticMigrationTest} 와 같은 방식이다.
 */
@DisplayName("훈장 마이그레이션")
class AchievementMigrationTest extends AchievementTestSupport {

    private static final String MIGRATION = "db/migration/V42__create_user_achievement.sql";

    @Test
    @DisplayName("DDL 은 다시 돌려도 안전하게 쓰여 있다")
    void ddlIsRerunnable() {
        String ddl = statements();

        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS USER_ACHIEVEMENT");
        // MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없다. 인덱스를 따로 만들면 그 문장이 재실행 지점이 된다.
        assertThat(ddl).doesNotContain("CREATE INDEX");
        // 훈장 목록은 코드의 enum 이라 시드가 없다. 시드가 섞이면 그 자리에서 끊길 때 테이블만 남는다.
        assertThat(ddl).doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("이미 테이블이 있는 스키마에 다시 돌려도 터지지 않는다")
    void rerunningDoesNotFail() {
        // 테스트 DB 에는 user_achievement 가 이미 있다(엔티티에서 Hibernate 가 만든다). 그 위에 두 번 더 돌린다.
        for (int attempt = 0; attempt < 2; attempt++) {
            runMigrationOnOneConnection();
        }

        assertThat(achievementRowCount(fixtures.createUser().getId())).isZero();
    }

    /**
     * 멱등성의 근거가 두 컬럼 복합 기본키라는 것을 잠근다.
     *
     * <p>컬럼 <b>순서</b>까지는 단언하지 않는다. 운영 스키마는 이 마이그레이션이 만들어
     * {@code (user_id, achievement_key)} 인데, 테스트 스키마는 Hibernate 가 {@code @IdClass} 의
     * 속성 이름 순으로 만들어 {@code (achievement_key, user_id)} 가 된다.
     * {@code user_cosmetic_loadout} 도 같은 사정이다. 중복을 막는 성질은 순서와 무관하므로
     * 여기서는 두 컬럼이 함께 기본키라는 것만 본다. 순서는 마이그레이션 본문으로 확인한다.
     */
    @Test
    @DisplayName("기본키가 (user_id, achievement_key) 복합키다 - 멱등성의 근거다")
    void primaryKeyIsComposite() {
        List<String> keyColumns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_achievement'
                  AND CONSTRAINT_NAME = 'PRIMARY'
                """, String.class);

        assertThat(keyColumns).containsExactlyInAnyOrder("user_id", "achievement_key");
        assertThat(statements())
                .as("운영 스키마는 user_id 가 선두여야 한다. 조회가 언제나 user_id 로 들어온다")
                .contains("PRIMARY KEY (USER_ID, ACHIEVEMENT_KEY)");
    }

    @Test
    @DisplayName("achievement_key 길이가 마이그레이션과 엔티티에서 같다")
    void keyColumnLengthMatchesMigration() {
        Long length = jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_achievement'
                  AND COLUMN_NAME = 'achievement_key'
                """, Long.class);

        assertThat(statements()).contains("ACHIEVEMENT_KEY VARCHAR(32)");
        assertThat(length)
                .as("엔티티의 length 와 마이그레이션이 갈리면 길이 초과 오류를 테스트가 재현하지 못한다")
                .isEqualTo(32L);
    }

    /**
     * 응원단장이 세는 리액션 세 테이블이 {@code user_id} 로 시작하는 인덱스를 갖고 있는지.
     *
     * <p>훈장 화면을 열 때마다 세 테이블에 {@code COUNT(*) WHERE user_id = ?} 가 나간다. 인덱스가
     * 없으면 그대로 풀스캔이고, 리액션은 사용자가 늘수록 가장 빨리 자라는 표 중 하나다.
     *
     * <p>지금은 인덱스를 새로 만들 필요가 없다. 세 테이블 모두 {@code user_id} 에 사용자 테이블을 향한
     * 외래키가 걸려 있어 InnoDB 가 {@code (user_id)} 단독 인덱스를 함께 만들어 두었고(V6, V11),
     * 댓글 리액션은 {@code idx_shared_problem_comment_reaction_user} 로 명시까지 돼 있다.
     * 같은 것을 한 벌 더 만들면 쓰기마다 갱신할 인덱스만 늘어난다.
     *
     * <p>그래서 이 테스트가 필요하다. 근거가 <b>외래키가 딸려 만든 인덱스</b>라, 나중에 외래키를 떼는
     * 변경이 있으면 인덱스도 조용히 같이 사라진다. 그때 훈장 화면이 느려지고 나서야 알게 되는 대신
     * 여기서 걸린다.
     */
    @Test
    @DisplayName("리액션 세 테이블이 user_id 선두 인덱스를 갖고 있다 - 응원단장 카운트가 이것을 탄다")
    void reactionTablesAreIndexedByUserId() {
        List<String> reactionTables = List.of(
                "study_room_feed_reaction",
                "study_room_shared_problem_reaction",
                "study_room_shared_problem_comment_reaction");

        for (String table : reactionTables) {
            Long indexCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.statistics
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = 'user_id'
                      AND SEQ_IN_INDEX = 1
                    """, Long.class, table);

            assertThat(indexCount)
                    .as(table + " 에 user_id 선두 인덱스가 없으면 훈장 화면을 열 때마다 풀스캔이 돈다")
                    .isNotNull()
                    .isPositive();
        }
    }

    private void runMigrationOnOneConnection() {
        List<String> statements = executableStatements();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return null;
        });
    }

    private List<String> executableStatements() {
        return Arrays.stream(withoutComments().split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    /** 주석을 걷어내고 대문자로 맞춘 본문. 공백 차이에 걸리지 않게 여러 공백은 하나로 줄인다. */
    private String statements() {
        return withoutComments().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String withoutComments() {
        return Arrays.stream(read().split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private String read() {
        try (InputStream inputStream = new ClassPathResource(MIGRATION).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
