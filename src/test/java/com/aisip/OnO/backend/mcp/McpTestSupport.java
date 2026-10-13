package com.aisip.OnO.backend.mcp;

import com.aisip.OnO.backend.admin.support.AdminTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * /mcp/admin 을 JSON-RPC 로 부르는 테스트의 공통 베이스.
 *
 * <p>MCP 서버 스타터는 전송 계층을 DispatcherServlet 의 RouterFunction 으로 등록하므로 MockMvc 로
 * 보안 체인까지 그대로 탄다. 토큰 값은 application-test.yml 의 mcp.admin.token 과 같다.
 */
public abstract class McpTestSupport extends AdminTestSupport {

    protected static final String ENDPOINT = "/mcp/admin";
    protected static final String ADMIN_TOKEN = "test-mcp-admin-token-0123456789abcdef";

    private final AtomicInteger requestId = new AtomicInteger();

    protected MvcResult rpc(String authorization, String method, Object params) throws Exception {
        Map<String, Object> body = params == null
                ? Map.of("jsonrpc", "2.0", "id", requestId.incrementAndGet(), "method", method)
                : Map.of("jsonrpc", "2.0", "id", requestId.incrementAndGet(), "method", method, "params", params);
        var request = post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(objectMapper.writeValueAsString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected JsonNode rpcResult(String method, Object params) throws Exception {
        MvcResult result = rpc("Bearer " + ADMIN_TOKEN, method, params);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.has("error")).as("JSON-RPC 오류: %s", body.path("error")).isFalse();
        return body.path("result");
    }

    /** 도구를 부르고 결과 본문(JSON 문자열)을 그대로 돌려준다. 개인정보가 섞였는지 문자열로 확인하려고 쓴다. */
    protected String callToolRaw(String name, Map<String, Object> arguments) throws Exception {
        JsonNode result = rpcResult("tools/call", Map.of("name", name, "arguments", arguments));
        assertThat(result.path("isError").asBoolean(false))
                .as("도구 %s 가 오류를 돌려줬다: %s", name, result.path("content"))
                .isFalse();
        return result.path("content").get(0).path("text").asText();
    }

    protected JsonNode callTool(String name, Map<String, Object> arguments) throws Exception {
        return objectMapper.readTree(callToolRaw(name, arguments));
    }

    /** 도구가 오류로 끝났을 때의 메시지. */
    protected String callToolError(String name, Map<String, Object> arguments) throws Exception {
        JsonNode result = rpcResult("tools/call", Map.of("name", name, "arguments", arguments));
        assertThat(result.path("isError").asBoolean(false)).as("도구 %s 가 오류여야 한다", name).isTrue();
        return result.path("content").get(0).path("text").asText();
    }
}
