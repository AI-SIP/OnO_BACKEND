package com.aisip.OnO.backend.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

@Slf4j
public class P6SpyExplainAppender implements MessageFormattingStrategy {

    // DataSource를 static으로 저장
    private static DataSource dataSource;

    // P6SpyConfig에서 호출
    public static void setDataSource(DataSource ds) {
        dataSource = ds;
    }

    // 시스템 프로퍼티로 제어 (P6SpyConfig에서 설정)
    private static boolean isExplainEnabled() {
        return Boolean.parseBoolean(System.getProperty("p6spy.enable.explain", "false"));
    }

    // EXPLAIN을 실행할 쿼리의 최소 실행 시간 (ms)
    // 0으로 설정하면 모든 SELECT 쿼리에 대해 EXPLAIN 실행
    private static final long SLOW_QUERY_THRESHOLD_MS = 0;

    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                 String category, String prepared, String sql, String url) {

        // Quartz 관련 쿼리 제외
        if (sql == null || sql.trim().isEmpty() || sql.contains("QRTZ_")) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        // 기본 로그 출력
        String formattedSql = formatSql(category, sql);
        result.append(String.format("[P6Spy] | %s | took %dms | %s | connection %d\n%s\n",
                now, elapsed, category, connectionId, formattedSql));

        // EXPLAIN 실행 조건 체크
        boolean explainEnabled = isExplainEnabled();
        boolean isSelect = sql.trim().toLowerCase(Locale.ROOT).startsWith("select");
        boolean isSlowQuery = elapsed >= SLOW_QUERY_THRESHOLD_MS;

        // ⭐ category 필터 제거: statement, commit 모두 EXPLAIN 실행
        // PreparedStatement도 분석하기 위해 category 조건을 완화
        boolean shouldExplain = explainEnabled && isSelect && isSlowQuery;

        if (shouldExplain) {
            try {
                String explainResult = executeExplain(sql, url);
                result.append("\n");
                result.append("╔════════════════════════════════════════════════════════════════════════════════╗\n");
                result.append("║                         EXPLAIN RESULT (took " + elapsed + "ms)                          ║\n");
                result.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
                result.append("║ Query: ").append(String.format("%-70s", sql.replaceAll("\\s+", " ").trim().substring(0, Math.min(70, sql.length())))).append(" ║\n");
                result.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
                result.append(explainResult);
                result.append("╚════════════════════════════════════════════════════════════════════════════════╝\n");
            } catch (Exception e) {
                log.warn("Failed to execute EXPLAIN for query: {}", e.getMessage());
            }
        }

        return result.toString();
    }

    private String formatSql(String category, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        if (category.equals("statement") && sql.trim().toLowerCase(Locale.ROOT).startsWith("select")) {
            return FormatStyle.BASIC.getFormatter().format(sql);
        }

        return sql;
    }

    private String executeExplain(String sql, String jdbcUrl) throws Exception {
        if (dataSource == null) {
            return "DataSource not available";
        }

        StringBuilder result = new StringBuilder();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {

            // 컬럼별 너비 설정 (보기 좋게 조정)
            String[] columnNames = new String[rs.getMetaData().getColumnCount()];
            int[] columnWidths = new int[columnNames.length];

            for (int i = 0; i < columnNames.length; i++) {
                columnNames[i] = rs.getMetaData().getColumnName(i + 1);
                // 컬럼별 최적 너비
                columnWidths[i] = getOptimalWidth(columnNames[i]);
            }

            // 헤더 출력 (세로 형식)
            result.append("\n");

            // 데이터 읽기
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                String[] row = new String[columnNames.length];
                for (int i = 0; i < columnNames.length; i++) {
                    row[i] = rs.getString(i + 1);
                    if (row[i] == null) row[i] = "NULL";
                }
                rows.add(row);
            }

            // 가독성 좋은 세로 형식으로 출력
            for (String[] row : rows) {
                for (int i = 0; i < columnNames.length; i++) {
                    result.append(String.format("  %-20s: %s\n", columnNames[i], row[i]));
                }
                result.append("\n");
            }
        }

        return result.toString();
    }

    private int getOptimalWidth(String columnName) {
        // 컬럼별 최적 너비 설정
        return switch (columnName.toLowerCase()) {
            case "id" -> 5;
            case "select_type" -> 12;
            case "table" -> 15;
            case "partitions" -> 12;
            case "type" -> 10;
            case "possible_keys" -> 25;
            case "key" -> 25;
            case "key_len" -> 10;
            case "ref" -> 15;
            case "rows" -> 10;
            case "filtered" -> 10;
            case "extra" -> 30;
            default -> 15;
        };
    }
}
