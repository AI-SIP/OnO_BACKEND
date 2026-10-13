package com.aisip.OnO.backend.mcp.tool;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** 도구 입력값 정리. AI 가 넘기는 값이라 비어 있거나 범위를 벗어나는 경우를 여기서 한 번에 막는다. */
final class McpToolArguments {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private McpToolArguments() {
    }

    static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 비어 있으면 null. 형식이 틀리면 AI 가 고쳐서 다시 부를 수 있게 이유를 담아 던진다. */
    static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " 는 yyyy-MM-dd 형식이어야 합니다: " + value);
        }
    }

    static int limit(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    static int page(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
