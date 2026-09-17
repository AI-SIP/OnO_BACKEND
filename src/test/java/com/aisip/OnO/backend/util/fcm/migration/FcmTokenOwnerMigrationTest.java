package com.aisip.OnO.backend.util.fcm.migration;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V43 이 실제 MySQL 8 에서 돌아가고 의도한 행만 남기는지 확인한다.
 *
 * <p>테스트는 Flyway 를 끄고 Hibernate 가 만든 스키마를 쓴다({@code application-test.yml}).
 * 그래서 Flyway 로 적용하는 대신 {@code AchievementMigrationTest} 처럼 파일 본문을 같은 연결에서 직접 실행한다.
 * MySQL 1093(DELETE 대상 테이블을 서브쿼리에서 읽기) 같은 문법 문제는 여기서 드러난다.
 */
@DisplayName("FCM 토큰 중복 소유 정리 마이그레이션")
class FcmTokenOwnerMigrationTest extends IntegrationTestSupport {

    private static final String MIGRATION = "db/migration/V43__dedupe_fcm_token_owner.sql";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("토큰마다 가장 최근에 만들어진 행 하나만 남긴다")
    void keepsLatestRowPerToken() {
        long a = fixtures.createUser().getId();
        long b = fixtures.createOtherUser().getId();
        long c = fixtures.createUser("third").getId();

        insert(10, a, "shared", BASE);
        insert(11, b, "shared", BASE.plusDays(1));
        insert(12, a, "a-tablet", BASE);
        insert(13, c, "c-phone", BASE);

        runMigration();

        assertThat(rows()).containsExactlyInAnyOrder(
                Map.entry(b, "shared"),
                Map.entry(a, "a-tablet"),
                Map.entry(c, "c-phone"));
    }

    @Test
    @DisplayName("생성 시각이 id 순서와 어긋나면 생성 시각을 따른다 - id 는 블록 단위로 미리 받는다")
    void ordersByCreatedAtBeforeId() {
        long a = fixtures.createUser().getId();
        long b = fixtures.createOtherUser().getId();

        insert(100, a, "shared", BASE.plusDays(1));
        insert(51, b, "shared", BASE);

        runMigration();

        assertThat(rows()).containsExactly(Map.entry(a, "shared"));
    }

    @Test
    @DisplayName("생성 시각이 같으면 id 가 큰 행을 남긴다")
    void breaksTieById() {
        long a = fixtures.createUser().getId();
        long b = fixtures.createOtherUser().getId();

        insert(20, a, "shared", BASE);
        insert(21, b, "shared", BASE);

        runMigration();

        assertThat(rows()).containsExactly(Map.entry(b, "shared"));
    }

    @Test
    @DisplayName("탈퇴한 사용자 행은 더 최근이어도 지우고, 지금 사용자 행을 남긴다")
    void removesWithdrawnUsersFirst() {
        long active = fixtures.createUser().getId();
        User leaver = fixtures.createOtherUser();
        jdbcTemplate.update("UPDATE `user` SET deleted_at = NOW() WHERE id = ?", leaver.getId());

        insert(30, active, "shared", BASE);
        insert(31, leaver.getId(), "shared", BASE.plusDays(1));
        insert(32, leaver.getId(), "leaver-only", BASE);

        runMigration();

        assertThat(rows()).containsExactly(Map.entry(active, "shared"));
    }

    @Test
    @DisplayName("다시 돌려도 터지지 않고 남은 행이 그대로다")
    void rerunIsSafe() {
        long a = fixtures.createUser().getId();
        long b = fixtures.createOtherUser().getId();
        insert(40, a, "shared", BASE);
        insert(41, b, "shared", BASE.plusDays(1));

        runMigration();
        runMigration();

        assertThat(rows()).containsExactly(Map.entry(b, "shared"));
    }

    private void insert(long id, long userId, String token, LocalDateTime createdAt) {
        Timestamp at = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                "INSERT INTO fcm_token (id, user_id, token, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, token, at, at);
    }

    private List<Map.Entry<Long, String>> rows() {
        return jdbcTemplate.query("SELECT user_id, token FROM fcm_token",
                (rs, rowNum) -> Map.entry(rs.getLong("user_id"), rs.getString("token")));
    }

    private void runMigration() {
        List<String> statements = Arrays.stream(withoutComments().split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return null;
        });
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
