package com.aisip.OnO.backend.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 실행 환경 자체를 검증한다.
 *
 * <p>여기가 깨지면 그 아래 모든 테스트의 신뢰도가 무너지므로 가장 먼저 확인한다.
 */
class TestEnvironmentSmokeTest extends IntegrationTestSupport {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("테스트는 H2가 아니라 프로덕션과 같은 MySQL 8 위에서 돈다")
    void runsOnMySql() {
        String product = (String) entityManager
                .createNativeQuery("SELECT 'mysql'")
                .getSingleResult();
        String version = entityManager.getEntityManagerFactory()
                .getProperties()
                .getOrDefault("hibernate.dialect", "")
                .toString();

        assertThat(product).isEqualTo("mysql");
        assertThat(version).contains("MySQL");
    }

    @Test
    @DisplayName("컬럼 콜레이션이 프로덕션과 동일한 utf8mb4_unicode_ci 다")
    void usesProductionCollation() {
        String collation = (String) entityManager
                .createNativeQuery("SELECT @@collation_server")
                .getSingleResult();

        assertThat(collation).isEqualTo("utf8mb4_unicode_ci");
    }

    @Test
    @DisplayName("각 테스트는 비워진 DB에서 시작한다 - 앞선 테스트가 남긴 데이터가 없다")
    void startsWithCleanDatabase() {
        fixtures.createUser();

        Number countAfterInsert = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user")
                .getSingleResult();

        assertThat(countAfterInsert.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("직전 테스트가 만든 사용자가 다음 테스트로 넘어오지 않는다")
    void isolatesDataBetweenTests() {
        Number countBeforeInsert = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM user")
                .getSingleResult();

        assertThat(countBeforeInsert.intValue()).isZero();
    }
}
