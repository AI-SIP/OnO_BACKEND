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
 *
 * <p>비우는 방식으로 TRUNCATE 가 아니라 DELETE 를 쓴다. InnoDB 에서 TRUNCATE 는
 * 테이블을 다시 만드는 DDL 이라 매번 디스크 플러시를 강제한다. 테이블 31개 × 테스트 1000개면
 * DDL 을 3만 번 실행하는 셈이라 스위트 전체가 사실상 끝나지 않았다.
 * DELETE 는 평범한 DML 이고, 이미 비어 있는 테이블에서는 거의 비용이 들지 않는다.
 *
 * <p>DELETE 는 AUTO_INCREMENT 를 되돌리지 않는다. 테스트가 특정 ID 값에 의존하면 안 된다는
 * 뜻인데, 어차피 그런 의존은 그 자체로 깨지기 쉬운 테스트다.
 */
@Component
public class DatabaseCleaner {

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
            entityManager.createNativeQuery("DELETE FROM `" + tableName + "`").executeUpdate();
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
                          AND table_name NOT LIKE '%\\_seq'
                          AND table_name <> 'hibernate_sequence'
                          AND table_name <> 'flyway_schema_history'
                        """)
                .getResultList();
    }
}
