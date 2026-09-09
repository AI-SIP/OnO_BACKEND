package com.aisip.OnO.backend.mission;

import com.aisip.OnO.backend.mission.support.MissionDefinitionSeeder;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    @Autowired
    private MissionDefinitionSeeder missionDefinitionSeeder;

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

    /** 주석을 걷어낸 실행 문장만. 주석에 적힌 설명이 단언에 걸리면 안 된다. */
    private String statementsOf(String path) {
        return Arrays.stream(read(path).split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .toUpperCase(Locale.ROOT);
    }

    private String read(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션을 읽지 못했다: " + path, e);
        }
    }
}
