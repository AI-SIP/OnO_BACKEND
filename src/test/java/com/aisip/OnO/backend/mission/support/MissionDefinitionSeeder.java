package com.aisip.OnO.backend.mission.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 미션 정의 시드를 테스트 DB 에 넣는다.
 *
 * <p>테스트 프로필은 Flyway 가 꺼져 있고 스키마를 Hibernate 가 만든다. 그래서 시드 INSERT 가
 * 저절로 실행되지 않는데, 미션 조회·진행도는 정의가 없으면 아무것도 하지 않으므로
 * 시드가 없으면 이 도메인 테스트는 전부 무의미해진다.
 *
 * <p>시드 내용을 테스트에 다시 적지 않고 <b>마이그레이션 파일의 INSERT 문을 그대로 읽어</b> 실행한다.
 * 같은 값을 두 곳에 적으면 한쪽만 고쳐질 때 테스트가 프로덕션 시드와 다른 것을 검증하게 된다.
 *
 * <p>{@code DatabaseCleaner} 가 테스트마다 모든 테이블을 비우므로 매 테스트 시작 시 다시 넣어야 한다.
 */
@Component
public class MissionDefinitionSeeder {

    private static final String MIGRATION_PATH = "db/migration/V29__create_mission_system.sql";

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> insertStatements;

    @Transactional
    public void seed() {
        if (insertStatements == null) {
            insertStatements = loadInsertStatements();
        }
        for (String statement : insertStatements) {
            entityManager.createNativeQuery(statement).executeUpdate();
        }
    }

    private List<String> loadInsertStatements() {
        String script = readMigration();

        // 주석 줄에도 세미콜론이 들어갈 수 있어 먼저 걷어낸다.
        String withoutComments = Arrays.stream(script.split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString();

        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> statement.toUpperCase(Locale.ROOT).startsWith("INSERT"))
                .toList();
    }

    private String readMigration() {
        try (InputStream inputStream = new ClassPathResource(MIGRATION_PATH).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("미션 시드 마이그레이션을 읽지 못했다: " + MIGRATION_PATH, e);
        }
    }
}
