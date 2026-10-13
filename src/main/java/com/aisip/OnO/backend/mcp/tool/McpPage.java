package com.aisip.OnO.backend.mcp.tool;

import java.util.List;

/** 목록 도구의 공통 응답. 전체 건수를 같이 줘야 AI 가 잘린 목록을 전부로 착각하지 않는다. */
public record McpPage<T>(long total, int returned, List<T> items) {

    public static <T> McpPage<T> of(long total, List<T> items) {
        return new McpPage<>(total, items.size(), items);
    }
}
