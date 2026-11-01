package com.aisip.OnO.backend.contract;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지금 코드가 내보내는 OpenAPI 스펙을 파일로 떨군다.
 *
 * <p>.github/workflows/api-contract.yml 이 PR 의 base 와 head 에서 각각 이 테스트를 돌려
 * 두 스펙을 oasdiff 로 비교한다. 응답 필드가 빠지거나 경로가 바뀌어도 지금까지는 배포 직전에
 * 사람이 diff 를 읽어야 알 수 있었는데, 출시된 앱은 심사를 거쳐야 고칠 수 있어서 PR 단계에서 알아야 한다.
 *
 * <p>base 브랜치에 이 테스트가 아직 없으면 workflow 가 head 의 이 파일을 base 체크아웃에 복사해서 돌린다.
 * 그래서 이 클래스는 {@link IntegrationTestSupport} 말고 다른 테스트 코드에 기대지 않게 둔다.
 */
@DisplayName("OpenAPI 스펙 내보내기")
class OpenApiSpecExportTest extends IntegrationTestSupport {

    static final Path OUTPUT = Path.of("build", "openapi", "openapi.json");

    @Test
    @DisplayName("/v3/api-docs 를 build/openapi/openapi.json 으로 저장한다")
    void exportsOpenApiSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode spec = objectMapper.readTree(body);
        // 빈 스펙끼리 비교하면 oasdiff 는 아무것도 못 찾고 통과한다. 그 상태를 여기서 막는다.
        assertThat(spec.path("openapi").asText()).startsWith("3.");
        assertThat(spec.path("paths").size()).isGreaterThan(10);

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec),
                StandardCharsets.UTF_8);
    }
}
