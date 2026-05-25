package com.aisip.OnO.backend.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

import java.util.Locale;

public class P6SpyExplainAppender implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                 String category, String prepared, String sql, String url) {
        if (sql == null || sql.trim().isEmpty() || containsQuartzTable(sql)) {
            return "";
        }

        String explainSql = toExplainSql(sql);
        return String.format("[P6Spy][SLOW_QUERY] took=%dms category=%s connection=%d sql=%s",
                elapsed, category, connectionId, explainSql);
    }

    private boolean containsQuartzTable(String sql) {
        return sql.toUpperCase(Locale.ROOT).contains("QRTZ_");
    }

    private String toExplainSql(String sql) {
        String normalizedSql = sql.replaceAll("\\s+", " ").trim();
        if (normalizedSql.endsWith(";")) {
            normalizedSql = normalizedSql.substring(0, normalizedSql.length() - 1).trim();
        }
        return "EXPLAIN " + normalizedSql + ";";
    }
}
