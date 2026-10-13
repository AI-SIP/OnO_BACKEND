package com.aisip.OnO.backend.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Spring AI MCP 서버 스타터 설정을 레포에 있는 {@code mcp-server.properties} 에서 읽는다.
 *
 * <p>{@code application*.yml} 은 CI 가 비밀값과 함께 만들어 레포에 없다. MCP 설정은 비밀이 아니라
 * 코드와 같이 리뷰돼야 해서 따로 둔다. {@code @PropertySource} 는 우선순위가 낮아서, 환경별로 바꿔야 하면
 * yml 이나 환경 변수로 덮어쓸 수 있다.
 */
@Configuration
@PropertySource(value = "classpath:mcp-server.properties", encoding = "UTF-8")
public class McpServerPropertiesConfig {
}
