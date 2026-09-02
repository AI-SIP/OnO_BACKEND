package com.aisip.OnO.backend.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 테스트 간 데이터 격리를 담당한다. 대상은 Testcontainers로 띄운 일회용 MySQL이며
 * dev/prod DB에는 어떤 경우에도 연결되지 않는다.
 *
 * <p>기존 스위트는 공유 인메모리 DB를 쓰면서 38개 클래스 중 7개만 롤백을 걸어,
 * 앞선 테스트가 남긴 행 때문에 "expected: 1 but was: 6" 류의 실패가 났다.
 * 각 테스트 시작 시 전체 테이블을 비워 실행 순서와 무관하게 동작하도록 만든다.
 */
@Component
public class DatabaseCleaner {

    private static final String CLEAR_TABLE_PREFIX = "TRUNCATE TABLE `";

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> tableNames;

    @Transactional
    public void clean() {
        entityManager.flush();
        entityManager.clear();

        if (tableNames == null) {
            tableNames = loadTableNames();
        }

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        for (String tableName : tableNames) {
            entityManager.createNativeQuery(CLEAR_TABLE_PREFIX + tableName + "`").executeUpdate();
        }
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> loadTableNames() {
        return entityManager.createNativeQuery("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_type = 'BASE TABLE'
                          AND table_name NOT LIKE 'QRTZ_%'
                          AND table_name <> 'flyway_schema_history'
                        """)
                .getResultList();
    }
}
