package com.aisip.OnO.backend.studyroom.migration;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V46 이 실제 MySQL 8 에서 돌아가고 기존 챌린지의 작성자를 방장으로 채우는지 확인한다.
 *
 * <p>테스트는 Flyway 를 끄고 Hibernate 가 만든 스키마를 쓴다({@code application-test.yml}).
 * 이 마이그레이션은 {@code ALTER TABLE ... ADD COLUMN} 이라, 엔티티에 이미 컬럼이 있는
 * {@code ono_test} 스키마에 그대로 돌리면 중복 컬럼으로 실패한다. 컬럼을 지우고 돌리는 방법도
 * 있지만 같은 스키마를 쓰는 다른 테스트가 휩쓸린다.
 *
 * <p>그래서 마이그레이션 전 모습만 담은 임시 스키마를 따로 만들고 그 안에서 파일 본문을 그대로
 * 실행한다. 스키마를 만들려면 권한이 있어야 해서 커넥션 풀 대신 root 로 직접 연결한다.
 * 애플리케이션 계정은 {@code ono_test} 에만 권한이 있다.
 */
@DisplayName("챌린지 작성자 컬럼 마이그레이션")
class StudyRoomChallengeCreatedByMigrationTest extends IntegrationTestSupport {

    private static final String MIGRATION = "db/migration/V46__add_study_room_challenge_created_by.sql";

    /** Testcontainers 의 MySQLContainer 는 root 비밀번호를 애플리케이션 계정 비밀번호와 같게 둔다. */
    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.password}")
    private String rootPassword;

    @Test
    @DisplayName("기존 챌린지의 작성자를 그 방의 방장으로 채우고 NOT NULL 로 조인다")
    void backfillsCreatorWithRoomHost() {
        inScratchSchema(statement -> {
            createPreMigrationTables(statement);
            statement.execute("INSERT INTO study_room VALUES (1, 100), (2, 200)");
            statement.execute("INSERT INTO study_room_challenge VALUES (10, 1, '가'), (11, 1, '나'), (12, 2, '다')");

            runMigration(statement);

            assertThat(creatorByChallengeId(statement))
                    .as("채워진 작성자")
                    .containsExactlyInAnyOrderEntriesOf(Map.of(10L, 100L, 11L, 100L, 12L, 200L));
            assertThat(isNullable(statement))
                    .as("created_by_user_id 가 NULL 을 허용하는지")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("챌린지가 하나도 없어도 컬럼만 생기고 끝난다")
    void runsOnEmptyTable() {
        inScratchSchema(statement -> {
            createPreMigrationTables(statement);

            runMigration(statement);

            assertThat(creatorByChallengeId(statement)).as("남은 행").isEmpty();
            assertThat(isNullable(statement)).as("created_by_user_id 가 NULL 을 허용하는지").isFalse();
        });
    }

    /** V46 직전의 두 테이블. 마이그레이션이 건드리는 컬럼만 남겼다. */
    private void createPreMigrationTables(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE study_room (
                    id BIGINT NOT NULL PRIMARY KEY,
                    host_user_id BIGINT NOT NULL
                )""");
        statement.execute("""
                CREATE TABLE study_room_challenge (
                    id BIGINT NOT NULL PRIMARY KEY,
                    room_id BIGINT NOT NULL,
                    title VARCHAR(40) NOT NULL
                )""");
    }

    private Map<Long, Long> creatorByChallengeId(Statement statement) throws Exception {
        Map<Long, Long> creators = new LinkedHashMap<>();
        try (ResultSet rs = statement.executeQuery(
                "SELECT id, created_by_user_id FROM study_room_challenge ORDER BY id")) {
            while (rs.next()) {
                creators.put(rs.getLong("id"), rs.getLong("created_by_user_id"));
            }
        }
        return creators;
    }

    private boolean isNullable(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'study_room_challenge'
                  AND column_name = 'created_by_user_id'""")) {
            assertThat(rs.next()).as("created_by_user_id 컬럼이 생겼는지").isTrue();
            return "YES".equals(rs.getString("is_nullable"));
        }
    }

    private void runMigration(Statement statement) throws Exception {
        for (String sql : statements()) {
            statement.execute(sql);
        }
    }

    private List<String> statements() {
        String body = Arrays.stream(read().split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        List<String> statements = new ArrayList<>();
        for (String sql : body.split(";")) {
            if (!sql.isBlank()) {
                statements.add(sql.trim());
            }
        }
        return statements;
    }

    private String read() {
        try (InputStream inputStream = new ClassPathResource(MIGRATION).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 임시 스키마 하나를 만들어 그 안에서만 작업하고 끝나면 지운다.
     *
     * <p>이름에 난수를 붙인다. 테스트가 한 번에 여러 개 돌아도 서로 부딪히지 않게 하기 위해서다.
     */
    private void inScratchSchema(ScratchWork work) {
        String schema = "v46_check_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", rootPassword);
             Statement statement = connection.createStatement()) {
            String original = currentDatabase(statement);
            statement.execute("CREATE DATABASE " + schema);
            try {
                // setCatalog 는 드라이버 설정에 따라 세션을 바꾸지 않을 수 있어 USE 를 직접 보낸다.
                statement.execute("USE " + schema);
                work.run(statement);
            } finally {
                statement.execute("USE " + original);
                statement.execute("DROP DATABASE " + schema);
            }
        } catch (Exception e) {
            throw new IllegalStateException("V46 마이그레이션 검증 실패", e);
        }
    }

    private String currentDatabase(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT DATABASE()")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @FunctionalInterface
    private interface ScratchWork {
        void run(Statement statement) throws Exception;
    }
}
