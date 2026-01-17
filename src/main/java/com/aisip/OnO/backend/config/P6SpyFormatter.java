package com.aisip.OnO.backend.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class P6SpyFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                 String category, String prepared, String sql, String url) {

        // Quartz 관련 쿼리 제외
        if (sql.trim().isEmpty() || sql.contains("QRTZ_")) {
            return "";
        }

        // SQL 포맷팅
        String formattedSql = formatSql(category, sql);

        return String.format("[P6Spy] | %s | took %dms | %s | connection %d\n%s",
                now, elapsed, category, connectionId, formattedSql);
    }

    private String formatSql(String category, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        // SELECT 쿼리만 포맷팅
        if (category.equals("statement") && sql.trim().toLowerCase(Locale.ROOT).startsWith("select")) {
            return FormatStyle.BASIC.getFormatter().format(sql);
        }

        return sql;
    }
}
