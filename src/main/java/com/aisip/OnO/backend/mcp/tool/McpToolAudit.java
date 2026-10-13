package com.aisip.OnO.backend.mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP 도구 호출을 한 줄씩 남긴다. 운영 데이터를 외부 AI 가 읽어 가는 경로라 누가 언제 무엇을 물었는지
 * 나중에 Loki 에서 찾을 수 있어야 한다. 응답 본문은 남기지 않고 건수만 남긴다.
 */
@Slf4j
@Component
public class McpToolAudit {

    public <T> T record(String tool, Map<String, ?> arguments, Supplier<T> body) {
        long startedAt = System.nanoTime();
        try {
            T result = body.get();
            log.info("MCP tool called - tool: {}, arguments: {}, outcome: success, size: {}, elapsedMs: {}",
                    tool, arguments, sizeOf(result), elapsedMs(startedAt));
            return result;
        } catch (RuntimeException e) {
            log.warn("MCP tool called - tool: {}, arguments: {}, outcome: error, error: {}, elapsedMs: {}",
                    tool, arguments, e.getMessage(), elapsedMs(startedAt));
            throw e;
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static int sizeOf(Object result) {
        if (result instanceof McpPage<?> page) {
            return page.items().size();
        }
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        return result == null ? 0 : 1;
    }
}
