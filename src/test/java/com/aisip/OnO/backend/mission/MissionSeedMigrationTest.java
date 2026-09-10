package com.aisip.OnO.backend.mission;

import com.aisip.OnO.backend.mission.support.MissionDefinitionSeeder;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션이 중간에 끊겨도 다시 돌릴 수 있는지.
 *
 * <p>MySQL DDL 은 트랜잭션이 아니다. 테이블은 만들어지고 뒤이은 INSERT 에서 끊기면 Flyway 는 실패로
 * 기록하는데 테이블은 남아, 재기동하면 "table already exists" 로 또 실패한다.
 * {@code flyway repair} 없이는 앱이 뜨지 않고, blue-green 배포 구간이면 그대로 장애다.
 */
@DisplayName("미션 마이그레이션")
class MissionSeedMigrationTest extends MissionSystemTestSupport {

    private static final String DDL_MIGRATION = "db/migration/V29__create_mission_system.sql";
    private static final String SEED_MIGRATION = "db/migration/V30__seed_mission_definitions.sql";
    private static final String CLAIMED_INDEX_MIGRATION = "db/migration/V31__add_mission_progress_claimed_index.sql";
    private static final String REWARD_SNAPSHOT_MIGRATION = "db/migration/V32__add_mission_progress_reward_snapshot.sql";
    private static final String WORDING_MIGRATION = "db/migration/V33__refine_mission_definition_wording.sql";

    @Autowired
    private MissionDefinitionSeeder missionDefinitionSeeder;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("DDL 파일에는 시드가 섞여 있지 않다")
    void ddlMigrationHasNoSeed() {
        assertThat(statementsOf(DDL_MIGRATION))
                .as("DDL 과 시드가 한 파일에 있으면 시드에서 끊길 때 테이블만 남아 재실행이 막힌다")
                .doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("DDL 은 다시 돌려도 안전하게 쓰여 있다")
    void ddlMigrationIsRerunnable() {
        String ddl = statementsOf(DDL_MIGRATION);

        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS MISSION_DEFINITION");
        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS MISSION_PROGRESS");
        // MySQL 에는 CREATE INDEX IF NOT EXISTS 가 없다. 인덱스를 따로 만들면 그 문장이 재실행 지점이 된다.
        assertThat(ddl).doesNotContain("CREATE INDEX");
    }

    @Test
    @DisplayName("시드는 다시 돌려도 안전하게 쓰여 있다")
    void seedMigrationIsRerunnable() {
        assertThat(statementsOf(SEED_MIGRATION)).contains("ON DUPLICATE KEY UPDATE");
    }

    @Test
    @DisplayName("인덱스와 컬럼 추가도 다시 돌려도 안전하게 쓰여 있다")
    void schemaChangeMigrationsAreRerunnable() {
        // MySQL 에는 CREATE INDEX IF NOT EXISTS 도 ADD COLUMN IF NOT EXISTS 도 없다.
        // DDL 이 커밋된 뒤 Flyway 가 이력을 남기기 전에 끊기면 재기동 때 Duplicate 로 앱이 뜨지 않는다.
        for (String migration : List.of(CLAIMED_INDEX_MIGRATION, REWARD_SNAPSHOT_MIGRATION)) {
            assertThat(statementsOf(migration))
                    .as("%s 가 existence 검사 없이 DDL 을 바로 실행한다", migration)
                    .contains("INFORMATION_SCHEMA");
        }
    }

    @Test
    @DisplayName("이미 적용된 스키마에 인덱스·컬럼 마이그레이션을 다시 돌려도 터지지 않는다")
    void rerunningSchemaMigrationsDoesNotFail() {
        // 테스트 DB 에는 이 인덱스와 컬럼이 이미 있다. 그 위에 두 번 더 돌려 본다.
        for (int attempt = 0; attempt < 2; attempt++) {
            runMigration(CLAIMED_INDEX_MIGRATION);
            runMigration(REWARD_SNAPSHOT_MIGRATION);
        }

        // 예외가 안 났다는 것만으로는 약하다. 다시 돌린 뒤에도 스키마가 온전한지 확인한다.
        assertThat(countIndex("mission_progress", "idx_mission_progress_claimed"))
                .as("인덱스가 사라지지도 중복으로 생기지도 않아야 한다")
                .isEqualTo(1);
        assertThat(countColumn("mission_progress", "reward_type_snapshot")).isEqualTo(1);
        assertThat(countColumn("mission_progress", "reward_value_snapshot")).isEqualTo(1);

        // 스키마가 실제로 쓸 수 있는 상태인지까지 본다.
        var user = fixtures.createUser();
        insertClaimedProgress(user.getId(), DAILY_NOTE_WRITE, "2020-09-01", LocalDateTime.of(2020, 9, 1, 10, 0));
        assertThat(missionService.getClaimHistory(user.getId(), null, 20).content()).hasSize(1);
    }

    /** 같은 이름의 인덱스가 몇 개인지. 복합 인덱스는 컬럼 수만큼 행이 나오므로 DISTINCT 로 센다. */
    private int countIndex(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, table, indexName);
        return count == null ? 0 : count;
    }

    private int countColumn(String table, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, columnName);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("문구 수정은 V30 을 고치지 않고 새 마이그레이션의 UPDATE 로 얹는다")
    void wordingIsAppliedAsUpdate() {
        // V30 을 고치면 이미 적용된 환경에서 Flyway 체크섬이 어긋나 앱이 뜨지 않는다.
        String wording = statementsOf(WORDING_MIGRATION);

        assertThat(wording).contains("UPDATE MISSION_DEFINITION");
        assertThat(wording).doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("정리한 문구가 그대로 내려간다")
    void appliesRefinedWording() {
        assertThat(definitionOf("DAILY_ATTEND").getDescription()).isEqualTo("앱 접속하기");
        assertThat(definitionOf("DAILY_NOTE_WRITE").getDescription()).isEqualTo("오답노트 1개 쓰기");
        assertThat(definitionOf("DAILY_REVIEW_3").getDescription()).isEqualTo("오답 3문제 복습하기");
        assertThat(definitionOf("DAILY_CORRECT_3").getDescription()).isEqualTo("복습에서 3문제 맞히기");
        assertThat(definitionOf("DAILY_PRACTICE_SET").getDescription()).isEqualTo("복습 세트 1개 끝내기");
        assertThat(definitionOf("DAILY_MOOD").getDescription()).isEqualTo("오늘 기분 남기기");
        assertThat(definitionOf("WEEKLY_ATTEND_5").getDescription()).isEqualTo("이번 주 5일 접속하기");
        assertThat(definitionOf("WEEKLY_NOTE_10").getDescription()).isEqualTo("오답노트 10개 쓰기");
        assertThat(definitionOf("WEEKLY_REVIEW_30").getDescription()).isEqualTo("오답 30문제 복습하기");
        assertThat(definitionOf("WEEKLY_SET_3").getDescription()).isEqualTo("복습 세트 3개 끝내기");

        assertThat(definitionOf("DAILY_CORRECT_3").getTitle()).isEqualTo("세 문제 맞히기");
        assertThat(definitionOf("DAILY_PRACTICE_SET").getTitle()).isEqualTo("복습 세트 완주");
        assertThat(definitionOf("WEEKLY_ATTEND_5").getTitle()).isEqualTo("닷새 접속하기");
        assertThat(definitionOf("WEEKLY_REVIEW_30").getTitle()).isEqualTo("서른 문제 복습");
    }

    @Test
    @DisplayName("시드를 두 번 실행해도 정의는 10종 그대로다")
    void seedIsIdempotent() {
        // @BeforeEach 가 이미 한 번 넣었다. 같은 문장을 다시 돌리는 것이 이 테스트의 핵심이다.
        missionDefinitionSeeder.seed();
        missionDefinitionSeeder.seed();

        assertThat(missionDefinitionRepository.findAll()).hasSize(10);
        assertThat(missionDefinitionRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc())
                .extracting(definition -> definition.getCategory().name())
                .filteredOn("DAILY"::equals)
                .hasSize(6);
    }

    /** 마이그레이션의 실행 문장을 순서대로 돌린다. */
    private void runMigration(String path) {
        transactionTemplate.executeWithoutResult(status -> {
            for (String statement : executableStatements(path)) {
                entityManager.createNativeQuery(statement).executeUpdate();
            }
        });
    }

    private List<String> executableStatements(String path) {
        return Arrays.stream(withoutComments(path).split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private String withoutComments(String path) {
        return Arrays.stream(read(path).split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    /** 주석을 걷어낸 실행 문장만. 주석에 적힌 설명이 단언에 걸리면 안 된다. */
    private String statementsOf(String path) {
        return withoutComments(path).toUpperCase(Locale.ROOT);
    }

    private String read(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션을 읽지 못했다: " + path, e);
        }
    }
}
