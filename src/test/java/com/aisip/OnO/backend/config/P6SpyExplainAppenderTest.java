package com.aisip.OnO.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class P6SpyExplainAppenderTest {

    private final P6SpyExplainAppender formatter = new P6SpyExplainAppender();

    @Test
    void formatMessage_printsSlowQueryAsSingleLineExplainSql() {
        String sql = """
                select *
                from users
                where email = 'test@example.com';
                """;

        String message = formatter.formatMessage(1, "2026-05-25 12:00:00", 301,
                "statement", sql, sql, "jdbc:mysql://localhost:3306/ono_db");

        assertThat(message).doesNotContain("\n");
        assertThat(message).contains("[P6Spy][SLOW_QUERY]");
        assertThat(message).contains("took=301ms");
        assertThat(message).contains("sql=EXPLAIN select * from users where email = 'test@example.com';");
    }

    @Test
    void formatMessage_skipsQuartzQueries() {
        String message = formatter.formatMessage(1, "2026-05-25 12:00:00", 301,
                "statement", "", "select * from QRTZ_TRIGGERS", "jdbc:mysql://localhost:3306/ono_db");

        assertThat(message).isEmpty();
    }
}
