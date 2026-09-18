package com.aisip.OnO.backend.util.fcm.migration;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V44 가 실제 MySQL 8 에서 돌아가는지 확인한다.
 *
 * <p>온라인 DDL 절({@code ALGORITHM=INPLACE, LOCK=NONE})은 문법이 틀리거나 이 서버에서 못 쓰면
 * 그 자리에서 실패한다. Flyway 는 앱이 뜰 때 도니까, 그 실패는 곧 기동 실패다.
 * 여기서 미리 실제 MySQL 8 에 적용해 본다.
 *
 * <p>테스트 스키마는 Hibernate 가 엔티티에서 만든다({@code application-test.yml}).
 * 엔티티에도 같은 인덱스를 선언해 두었으므로 이미 붙어 있고, 그대로 ALTER 하면 이름 충돌로 실패한다.
 * 그래서 지웠다가 마이그레이션으로 다시 만든다.
 */
@DisplayName("FCM 토큰 인덱스 마이그레이션")
class FcmTokenIndexMigrationTest extends IntegrationTestSupport {

    private static final String MIGRATION = "db/migration/V44__add_fcm_token_token_index.sql";
    private static final String INDEX_NAME = "idx_fcm_token_token";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("token 단독 인덱스를 온라인 DDL 로 추가한다")
    void addsTokenIndexOnline() {
        dropIndexIfExists();
        assertThat(indexCount()).isZero();

        jdbcTemplate.execute(withoutComments().trim().replaceAll(";$", ""));

        assertThat(indexCount())
                .as("token 단독 인덱스가 없으면 등록 때마다 fcm_token 을 전부 읽는다")
                .isEqualTo(1);
        assertThat(isUnique())
                .as("중복 쌍이 남아 있어도 ALTER 가 실패하지 않으려면 유니크가 아니어야 한다")
                .isFalse();
    }

    private void dropIndexIfExists() {
        if (indexCount() > 0) {
            jdbcTemplate.execute("DROP INDEX " + INDEX_NAME + " ON fcm_token");
        }
    }

    private Integer indexCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'fcm_token'
                  AND index_name = ?
                """, Integer.class, INDEX_NAME);
    }

    private boolean isUnique() {
        Integer nonUnique = jdbcTemplate.queryForObject("""
                SELECT non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'fcm_token'
                  AND index_name = ?
                """, Integer.class, INDEX_NAME);
        return nonUnique != null && nonUnique == 0;
    }

    private String withoutComments() {
        return Arrays.stream(read().split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private String read() {
        try (InputStream inputStream = new ClassPathResource(MIGRATION).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
