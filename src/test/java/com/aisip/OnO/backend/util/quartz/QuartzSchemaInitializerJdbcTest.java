package com.aisip.OnO.backend.util.quartz;

import com.aisip.OnO.backend.support.TestContainers;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code initialize-schema: always} 인 채로 기동해도 기존 QRTZ_ 테이블이 지워지지 않는지 본다.
 *
 * <p>이슈 #288. 자동 구성의 초기화 빈은 매 기동마다 Quartz 기본 스크립트를 실행했고, 그 스크립트가
 * DROP TABLE 로 시작해서 사용자가 등록한 알림 트리거가 배포마다 사라졌다.
 *
 * <p>스프링 컨텍스트 없이 초기화 빈만 직접 만들어 확인한다. 테스트 프로필은 메모리 잡스토어라
 * 컨텍스트로는 이 경로가 아예 타지 않는다. 다른 테스트가 쓰는 {@code ono_test} 스키마를 건드리지
 * 않도록 전용 스키마를 따로 만든다.
 */
@DisplayName("Quartz 스키마 초기화 - 기존 테이블 보존")
class QuartzSchemaInitializerJdbcTest {

    private static final String SCHEMA = "quartz_schema_initializer_test";

    private static final String[] QUARTZ_TABLES = {
            "QRTZ_FIRED_TRIGGERS",
            "QRTZ_PAUSED_TRIGGER_GRPS",
            "QRTZ_SCHEDULER_STATE",
            "QRTZ_LOCKS",
            "QRTZ_SIMPLE_TRIGGERS",
            "QRTZ_SIMPROP_TRIGGERS",
            "QRTZ_CRON_TRIGGERS",
            "QRTZ_BLOB_TRIGGERS",
            "QRTZ_TRIGGERS",
            "QRTZ_JOB_DETAILS",
            "QRTZ_CALENDARS"
    };

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        MySQLContainer<?> mysql = TestContainers.mysql();
        String baseUrl = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(MySQLContainer.MYSQL_PORT);

        // 테스트 계정은 ono_test 에만 권한이 있어 스키마 생성은 root 로 한다.
        try (Connection conn = DriverManager.getConnection(baseUrl, "root", mysql.getPassword());
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + SCHEMA);
        }

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(baseUrl + "/" + SCHEMA);
        dataSource.setUsername("root");
        dataSource.setPassword(mysql.getPassword());
        dataSource.setMaximumPoolSize(2);

        jdbcTemplate = new JdbcTemplate(dataSource);

        // "QRTZ_ 테이블이 없는 환경" 을 재현해야 하므로 테이블만 비우고 시작한다.
        // 스키마 자체를 지우지 않는 것은 컨테이너를 재사용하는 다른 테스트와 겹치지 않기 위해서다.
        dropQuartzTables();
    }

    private void dropQuartzTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : QUARTZ_TABLES) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + table);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("QRTZ_ 테이블이 이미 있으면 always 여도 스크립트를 실행하지 않아 기존 행이 남는다")
    void keepsExistingTablesWhenSchemaAlreadyExists() {
        createQuartzSchema();
        jdbcTemplate.update(
                "INSERT INTO QRTZ_LOCKS (SCHED_NAME, LOCK_NAME) VALUES (?, ?)",
                "quartzScheduler", "MARKER_288");

        boolean applied = newInitializer(DatabaseInitializationMode.ALWAYS).initializeDatabase();

        assertThat(applied).isFalse();
        assertThat(countMarkerRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("QRTZ_ 테이블이 없으면 스크립트를 실행해 만든다")
    void createsSchemaWhenMissing() {
        assertThat(quartzTableCount()).isZero();

        boolean applied = newInitializer(DatabaseInitializationMode.ALWAYS).initializeDatabase();

        assertThat(applied).isTrue();
        assertThat(quartzTableCount()).isEqualTo(11);
    }

    @Test
    @DisplayName("스크립트를 한 번 실행한 뒤 다시 기동해도 그때 만든 행이 남는다")
    void keepsRowsAcrossRestarts() {
        newInitializer(DatabaseInitializationMode.ALWAYS).initializeDatabase();
        jdbcTemplate.update(
                "INSERT INTO QRTZ_LOCKS (SCHED_NAME, LOCK_NAME) VALUES (?, ?)",
                "quartzScheduler", "MARKER_288");

        boolean applied = newInitializer(DatabaseInitializationMode.ALWAYS).initializeDatabase();

        assertThat(applied).isFalse();
        assertThat(countMarkerRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("V45 마이그레이션은 두 번 돌려도 스키마를 다시 만들지 않는다")
    void migrationIsIdempotent() {
        applyMigration();
        assertThat(quartzTableCount()).isEqualTo(11);

        jdbcTemplate.update(
                "INSERT INTO QRTZ_LOCKS (SCHED_NAME, LOCK_NAME) VALUES (?, ?)",
                "quartzScheduler", "MARKER_288");
        applyMigration();

        assertThat(quartzTableCount()).isEqualTo(11);
        assertThat(countMarkerRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("V45 로 만든 스키마에서는 초기화 빈이 스크립트를 실행하지 않는다")
    void initializerSkipsAfterMigration() {
        applyMigration();

        boolean applied = newInitializer(DatabaseInitializationMode.ALWAYS).initializeDatabase();

        assertThat(applied).isFalse();
    }

    private void applyMigration() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V45__create_quartz_tables.sql"));
        populator.execute(dataSource);
    }

    private QuartzSchemaInitializer newInitializer(DatabaseInitializationMode mode) {
        QuartzProperties properties = new QuartzProperties();
        properties.getJdbc().setInitializeSchema(mode);
        properties.getProperties().put("org.quartz.jobStore.tablePrefix", "QRTZ_");

        QuartzSchemaInitializer initializer = new QuartzSchemaInitializer(dataSource, properties);
        initializer.setResourceLoader(new DefaultResourceLoader());
        return initializer;
    }

    private void createQuartzSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql"));
        populator.setCommentPrefixes("#", "--");
        populator.execute(dataSource);
    }

    private int countMarkerRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QRTZ_LOCKS WHERE LOCK_NAME = ?", Integer.class, "MARKER_288");
        return count == null ? 0 : count;
    }

    private int quartzTableCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name LIKE 'QRTZ\\_%'",
                Integer.class, SCHEMA);
        return count == null ? 0 : count;
    }
}
