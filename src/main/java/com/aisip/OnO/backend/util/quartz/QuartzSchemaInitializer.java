package com.aisip.OnO.backend.util.quartz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * QRTZ_ 테이블이 이미 있으면 Quartz 스키마 초기화 스크립트를 실행하지 않는다.
 *
 * <p>Spring Boot 의 {@code spring.quartz.jdbc.initialize-schema: always} 는 기동할 때마다
 * Quartz 가 제공하는 {@code tables_mysql_innodb.sql} 을 그대로 실행한다. 그 스크립트는
 * {@code DROP TABLE IF EXISTS QRTZ_...} 11 줄로 시작한다. 그래서 배포할 때마다 QRTZ_ 테이블이
 * 통째로 다시 만들어졌고, 사용자가 등록한 복습노트 알림 트리거({@code trigger-{practiceId}})와
 * 챌린지 알림 트리거가 전부 사라졌다. 기동할 때 {@code @PostConstruct} 로 다시 등록하는
 * 고정 트리거 세 개만 살아남았다.
 *
 * <p>운영에 쓰이는 {@code application-prod.yml} 은 레포에 없고 CI secret 으로 주입된다.
 * 설정 파일만 고쳐서는 운영에 반영되지 않으므로, 설정이 {@code always} 인 채로 배포되더라도
 * 테이블이 지워지지 않게 코드에서 막는다.
 *
 * <p>자동 구성의 초기화 빈은 {@code @ConditionalOnMissingBean(QuartzDataSourceScriptDatabaseInitializer.class)}
 * 로 등록된다({@code QuartzAutoConfiguration.JdbcStoreTypeConfiguration}). 같은 타입인 이 빈을
 * 직접 정의하면 자동 구성이 물러난다.
 *
 * <p>테이블이 없을 때는 상위 구현에 그대로 위임한다. QRTZ_ 스키마는 Flyway
 * {@code V45__create_quartz_tables.sql} 이 만들고, Flyway 는 이 초기화 빈보다 먼저 돈다
 * ({@code DataSourceScriptDatabaseInitializerDetector} 의 순서가 Flyway 감지기보다 뒤라
 * 스크립트 초기화 빈이 Flyway 에 의존하도록 엮인다). 즉 정상 경로에서는 여기서 스크립트가
 * 실행될 일이 없고, Flyway 를 끈 환경을 위한 대비책으로만 남는다.
 */
public class QuartzSchemaInitializer extends QuartzDataSourceScriptDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(QuartzSchemaInitializer.class);

    private static final String TABLE_PREFIX_PROPERTY = "org.quartz.jobStore.tablePrefix";
    private static final String DEFAULT_TABLE_PREFIX = "QRTZ_";

    private final DataSource dataSource;
    private final String triggersTableName;

    public QuartzSchemaInitializer(DataSource dataSource, QuartzProperties properties) {
        super(dataSource, properties);
        this.dataSource = dataSource;
        this.triggersTableName = resolveTablePrefix(properties) + "TRIGGERS";
    }

    @Override
    public boolean initializeDatabase() {
        if (quartzSchemaExists()) {
            log.info("Quartz 스키마가 이미 있어 초기화 스크립트를 건너뛴다. (기준 테이블: {})", triggersTableName);
            return false;
        }

        log.info("Quartz 스키마가 없어 초기화 스크립트를 실행한다. (기준 테이블: {})", triggersTableName);
        return super.initializeDatabase();
    }

    /**
     * 트리거 테이블이 이미 있는지 본다.
     *
     * <p>확인에 실패하면 "있다"로 답한다. 초기화 스크립트는 되돌릴 수 없는 DROP 으로 시작하므로,
     * 상태를 모르는 채로 실행하는 쪽이 건너뛰는 쪽보다 훨씬 위험하다. 테이블이 정말 없었다면
     * 잠시 뒤 스케줄러가 기동하면서 명확한 오류로 드러난다.
     */
    private boolean quartzSchemaExists() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(
                    connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (triggersTableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            log.warn("Quartz 스키마 존재 여부를 확인하지 못했다. 초기화 스크립트를 실행하지 않는다.", e);
            return true;
        }
    }

    private static String resolveTablePrefix(QuartzProperties properties) {
        String prefix = properties.getProperties().get(TABLE_PREFIX_PROPERTY);
        return StringUtils.hasText(prefix) ? prefix : DEFAULT_TABLE_PREFIX;
    }
}
