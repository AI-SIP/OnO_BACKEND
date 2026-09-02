package com.aisip.OnO.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 전역에서 단 한 번만 기동하는 인프라 컨테이너.
 *
 * <p>기존 테스트는 H2(MODE=MySQL)를 썼기 때문에 프로덕션 MySQL에서만 터지는 문제
 * (컬럼 길이 초과로 인한 Data truncation, 유니크 인덱스 충돌, 콜레이션 차이)를
 * 구조적으로 재현할 수 없었다. 프로덕션과 동일한 MySQL 8 위에서 검증한다.
 *
 * <p>JVM 종료 시 Ryuk이 정리하므로 stop()을 명시 호출하지 않는다.
 */
public final class TestContainers {

    private static final MySQLContainer<?> MYSQL;
    private static final GenericContainer<?> REDIS;
    private static final RabbitMQContainer RABBITMQ;

    static {
        MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("ono_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--skip-character-set-client-handshake"
                )
                .withReuse(true);

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        RABBITMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withReuse(true);

        MYSQL.start();
        REDIS.start();
        RABBITMQ.start();
    }

    private TestContainers() {
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainers::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    /** 마이그레이션 검증 테스트가 별도 스키마를 만들 때 쓴다. */
    public static MySQLContainer<?> mysql() {
        return MYSQL;
    }

    private static String jdbcUrl() {
        // rewriteBatchedStatements: 배치 insert 동작을 프로덕션 설정과 맞춘다.
        return MYSQL.getJdbcUrl() + "?rewriteBatchedStatements=true&characterEncoding=UTF-8";
    }
}
