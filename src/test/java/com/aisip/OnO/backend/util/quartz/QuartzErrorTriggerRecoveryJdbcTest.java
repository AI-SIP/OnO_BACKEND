package com.aisip.OnO.backend.util.quartz;

import com.aisip.OnO.backend.support.TestContainers;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영과 같은 JDBC 잡스토어(MySQL + StdJDBCDelegate)에서 ERROR 트리거가 실제로 되돌아가는지 확인한다.
 *
 * <p>테스트 프로필은 메모리 잡스토어를 쓰므로 스프링 컨텍스트와 무관하게 스케줄러를 따로 띄운다.
 * QRTZ_ 테이블은 Quartz 가 제공하는 MySQL 스크립트로 별도 스키마에 만들어, 다른 테스트가 쓰는
 * {@code ono_test} 스키마를 건드리지 않는다. 스크립트에 DROP TABLE 이 들어 있어 매번 새로 만든다.
 *
 * <p>잡은 아무 일도 하지 않는 {@link NoOpJob} 이라 FCM 등 외부 발송 경로와 무관하다.
 */
@DisplayName("Quartz ERROR 트리거 복구 - JDBC 잡스토어")
class QuartzErrorTriggerRecoveryJdbcTest {

    private static final String SCHEMA = "quartz_trigger_recovery_test";
    private static final JobKey JOB_KEY = JobKey.jobKey("problem-review-reminder", "reminder");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("problem-review-reminder-trigger", "reminder");
    private static final String MISSING_CLASS = "com.aisip.OnO.backend.problem.reminder.NotInThisImageJob";

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private SchedulerFactoryBean factoryBean;
    private Scheduler scheduler;
    private QuartzErrorTriggerRecoverer recoverer;

    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MySQLContainer<?> mysql = TestContainers.mysql();
        String baseUrl = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(MySQLContainer.MYSQL_PORT);

        // 테스트 계정은 ono_test 에만 권한이 있어 스키마 생성은 root 로 한다.
        // Testcontainers 는 root 비밀번호를 테스트 계정 비밀번호와 같게 둔다.
        try (Connection conn = DriverManager.getConnection(baseUrl, "root", mysql.getPassword());
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + SCHEMA);
        }

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(baseUrl + "/" + SCHEMA);
        dataSource.setUsername("root");
        dataSource.setPassword(mysql.getPassword());
        dataSource.setMaximumPoolSize(4);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql"));
        populator.setCommentPrefixes("#", "--");
        populator.execute(dataSource);

        jdbcTemplate = new JdbcTemplate(dataSource);

        Properties properties = new Properties();
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        properties.setProperty("org.quartz.jobStore.isClustered", "false");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");

        factoryBean = new SchedulerFactoryBean();
        factoryBean.setSchedulerName("quartz-error-trigger-recovery-test");
        factoryBean.setDataSource(dataSource);
        factoryBean.setQuartzProperties(properties);
        factoryBean.setAutoStartup(false);
        factoryBean.afterPropertiesSet();
        scheduler = factoryBean.getObject();

        recoverer = new QuartzErrorTriggerRecoverer(scheduler);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void registerReminderJob(Trigger trigger) throws Exception {
        JobDetail jobDetail = JobBuilder.newJob(NoOpJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably(true)
                .build();
        scheduler.addJob(jobDetail, true);
        scheduler.scheduleJob(trigger);
    }

    private Trigger cronTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 */5 * ? * *"))
                .build();
    }

    private String storedState() {
        return jdbcTemplate.queryForObject(
                "SELECT TRIGGER_STATE FROM QRTZ_TRIGGERS WHERE TRIGGER_NAME = ? AND TRIGGER_GROUP = ?",
                String.class, TRIGGER_KEY.getName(), TRIGGER_KEY.getGroup());
    }

    private void setJobClass(String className) {
        jdbcTemplate.update("UPDATE QRTZ_JOB_DETAILS SET JOB_CLASS_NAME = ? WHERE JOB_NAME = ? AND JOB_GROUP = ?",
                className, JOB_KEY.getName(), JOB_KEY.getGroup());
    }

    @Test
    @DisplayName("ERROR 로 바뀐 트리거를 WAITING 으로 되돌린다")
    void resetsErrorTriggerToWaiting() throws Exception {
        registerReminderJob(cronTrigger());
        jdbcTemplate.update("UPDATE QRTZ_TRIGGERS SET TRIGGER_STATE = 'ERROR' WHERE TRIGGER_NAME = ?", TRIGGER_KEY.getName());
        assertThat(scheduler.getTriggerState(TRIGGER_KEY)).isEqualTo(Trigger.TriggerState.ERROR);

        assertThat(recoverer.recoverErrorTriggers()).isEqualTo(1);

        assertThat(storedState()).isEqualTo("WAITING");
        assertThat(scheduler.getTriggerState(TRIGGER_KEY)).isEqualTo(Trigger.TriggerState.NORMAL);
    }

    @Test
    @DisplayName("blue 와 green 이 연달아 점검해도 두 번째는 아무것도 바꾸지 않는다")
    void secondRecoveryIsNoOp() throws Exception {
        registerReminderJob(cronTrigger());
        jdbcTemplate.update("UPDATE QRTZ_TRIGGERS SET TRIGGER_STATE = 'ERROR' WHERE TRIGGER_NAME = ?", TRIGGER_KEY.getName());

        QuartzErrorTriggerRecoverer otherInstance = new QuartzErrorTriggerRecoverer(scheduler);

        assertThat(recoverer.recoverErrorTriggers()).isEqualTo(1);
        assertThat(otherInstance.recoverErrorTriggers()).isZero();
        assertThat(storedState()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("구 이미지가 트리거를 집어 ERROR 가 된 상황을 재현하고, 잡 클래스를 아는 인스턴스만 되돌린다")
    void reproducesClassNotFoundAndRecovers() throws Exception {
        // 곧바로 발화할 트리거를 두고, 잡 클래스를 이 JVM 에 없는 이름으로 바꿔 구 컨테이너의 시야를 흉내 낸다.
        registerReminderJob(TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .startAt(new Date())
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 1 1 ? 2099"))
                .build());
        jdbcTemplate.update("UPDATE QRTZ_TRIGGERS SET NEXT_FIRE_TIME = ? WHERE TRIGGER_NAME = ?",
                System.currentTimeMillis() - 1_000, TRIGGER_KEY.getName());
        setJobClass(MISSING_CLASS);

        scheduler.start();
        waitUntilStored("ERROR");
        scheduler.standby();

        // 잡 클래스를 모르는 인스턴스는 되돌리지 않는다. 되돌려 봐야 자기가 다시 ERROR 로 만든다.
        assertThat(recoverer.recoverErrorTriggers()).isZero();
        assertThat(storedState()).isEqualTo("ERROR");

        // 새 이미지(잡 클래스를 아는 인스턴스)의 시야로 돌아오면 되돌린다.
        setJobClass(NoOpJob.class.getName());
        assertThat(recoverer.recoverErrorTriggers()).isEqualTo(1);
        assertThat(storedState()).isEqualTo("WAITING");
    }

    private void waitUntilStored(String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(storedState())) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(storedState()).as("스케줄러가 트리거를 집어 상태를 바꿔야 한다").isEqualTo(expected);
    }
}
