package com.aisip.OnO.backend.cosmetic.support;

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
 * 꾸미기 아이템 시드를 테스트 DB 에 넣는다.
 *
 * <p>테스트 프로필은 Flyway 가 꺼져 있고 스키마를 Hibernate 가 만든다. 그래서 시드 INSERT 가
 * 저절로 실행되지 않는데, 꾸미기 조회·장착은 카탈로그가 없으면 아무것도 하지 못하므로
 * 시드가 없으면 이 도메인 테스트는 전부 무의미해진다.
 *
 * <p>시드 내용을 테스트에 다시 적지 않고 <b>마이그레이션 파일의 INSERT 문을 그대로 읽어</b> 실행한다.
 * 같은 값을 두 곳에 적으면 한쪽만 고쳐질 때 테스트가 프로덕션 시드와 다른 것을 검증하게 된다.
 * {@code MissionDefinitionSeeder} 와 같은 방식이다.
 *
 * <p>{@code DatabaseCleaner} 가 테스트마다 모든 테이블을 비우므로 매 테스트 시작 시 다시 넣어야 한다.
 */
@Component
public class CosmeticItemSeeder {

    /** 카탈로그를 손대는 마이그레이션을 새로 만들면 적용 순서대로 여기에 더한다. */
    private static final List<String> MIGRATION_PATHS = List.of(
            "db/migration/V35__seed_cosmetic_items.sql"
    );

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> statements;

    @Transactional
    public void seed() {
        if (statements == null) {
            statements = MIGRATION_PATHS.stream()
                    .flatMap(path -> loadStatements(path).stream())
                    .toList();
        }
        for (String statement : statements) {
            entityManager.createNativeQuery(statement).executeUpdate();
        }
    }

    private List<String> loadStatements(String path) {
        // 주석 줄에도 세미콜론이 들어갈 수 있어 먼저 걷어낸다.
        String withoutComments = Arrays.stream(readMigration(path).split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString();

        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> {
                    String upper = statement.toUpperCase(Locale.ROOT);
                    return upper.startsWith("INSERT") || upper.startsWith("UPDATE");
                })
                .toList();
    }

    private String readMigration(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("꾸미기 시드 마이그레이션을 읽지 못했다: " + path, e);
        }
    }
}
