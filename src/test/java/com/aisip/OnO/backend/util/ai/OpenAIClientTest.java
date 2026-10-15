package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 연동 클라이언트.
 *
 * <p>HTTP 호출은 {@link MockRestServiceServer} 로 가로채므로 실제 OpenAI 로 나가지 않는다.
 * 이미지 로더는 목이라 S3 도 읽지 않는다(로더 자체는 AnalysisImageLoaderTest).
 * 프로덕션에서 실제로 관측된 응답(거절 문구, 할당량 초과 429)을 그대로 재현하고,
 * 요청 본문(Structured Outputs, 프롬프트, data URL)도 검증한다.
 */
@DisplayName("OpenAI 클라이언트")
class OpenAIClientTest {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String IMAGE_URL = "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/image/a.jpg";
    private static final String IMAGE_URL_2 = "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/image/b.jpg";
    private static final String DATA_URL = "data:image/jpeg;base64,/9j/AAAA";
    private static final String DATA_URL_2 = "data:image/png;base64,iVBORw0K";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MeterRegistry meterRegistry;
    private AnalysisImageLoader imageLoader;
    private OpenAIClient openAIClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        meterRegistry = new SimpleMeterRegistry();
        imageLoader = mock(AnalysisImageLoader.class);
        when(imageLoader.loadAsDataUrls(List.of(IMAGE_URL))).thenReturn(List.of(DATA_URL));
        when(imageLoader.loadAsDataUrls(List.of(IMAGE_URL, IMAGE_URL_2))).thenReturn(List.of(DATA_URL, DATA_URL_2));
        openAIClient = new OpenAIClient(restTemplate, objectMapper, meterRegistry, imageLoader);
        ReflectionTestUtils.setField(openAIClient, "model", "gpt-4o");
        ReflectionTestUtils.setField(openAIClient, "apiKey", "test-openai-api-key");
        ReflectionTestUtils.setField(openAIClient, "apiUrl", API_URL);
        ReflectionTestUtils.setField(openAIClient, "learningReportAiEnabled", true);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    // ---- 응답 만들기 ----

    private void respondWithBody(String body) {
        server.expect(requestTo(API_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-openai-api-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    /** 요청 본문을 잡아 두고 응답한다 */
    private AtomicReference<JsonNode> captureRequestAndRespond(String responseBody) {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        server.expect(requestTo(API_URL))
                .andExpect(request -> captured.set(objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        return captured;
    }

    private void respondWithContent(String content) {
        respondWithBody(chatCompletionJson(content, "stop", null));
    }

    private String chatCompletionJson(String content, String finishReason, String usageJson) {
        try {
            String message = "{\"role\":\"assistant\",\"content\":" + objectMapper.writeValueAsString(content)
                    + ",\"refusal\":null}";
            return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"model\":\"gpt-4o-2024-08-06\","
                    + "\"choices\":[{\"index\":0,\"message\":" + message + ",\"logprobs\":null,"
                    + "\"finish_reason\":" + objectMapper.writeValueAsString(finishReason) + "}]"
                    + (usageJson == null ? "" : ",\"usage\":" + usageJson)
                    + ",\"system_fingerprint\":\"fp_x\",\"service_tier\":\"default\"}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String analysisJson() {
        return """
                {
                  "analyzable": true,
                  "subject": "수학",
                  "problemType": "이차방정식",
                  "keyPoints": ["근의 공식", "판별식"],
                  "solution": "먼저 조건을 정리하고 판별식을 세워보세요.",
                  "commonMistakes": "부호를 놓치기 쉽습니다.",
                  "studyTips": "교과서 2단원을 복습하세요."
                }
                """;
    }

    private Timer externalTimer(String operation, String outcome) {
        return meterRegistry.find("ono.external.requests")
                .tags("dependency", "openai", "operation", operation, "outcome", outcome)
                .timer();
    }

    private Counter tokenCounter(String operation, String type) {
        return meterRegistry.find("ono.ai.tokens")
                .tags("operation", operation, "model", "gpt-4o", "type", type)
                .counter();
    }

    private static AnalysisFailureReason reasonOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(NonRetryableAnalysisException.class);
        return ((NonRetryableAnalysisException) thrown).getReason();
    }

    /** 본문 어디에도 null 값이 없어야 한다 */
    private static List<String> nullPaths(JsonNode node, String path) {
        List<String> result = new ArrayList<>();
        if (node.isNull()) {
            result.add(path);
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.addAll(nullPaths(field.getValue(), path + "." + field.getKey()));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                result.addAll(nullPaths(node.get(i), path + "[" + i + "]"));
            }
        }
        return result;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ---- 테스트 ----

    @Nested
    @DisplayName("이미지 분석 - 요청 본문")
    class AnalyzeRequestBody {

        @Test
        @DisplayName("Structured Outputs json_schema(strict) 로 7개 필드를 모두 required 로 요구한다")
        void sendsStrictJsonSchema() {
            AtomicReference<JsonNode> body = captureRequestAndRespond(chatCompletionJson(analysisJson(), "stop", null));

            openAIClient.analyzeImage(IMAGE_URL);

            JsonNode format = body.get().get("response_format");
            assertThat(format.get("type").asText()).isEqualTo("json_schema");
            JsonNode jsonSchema = format.get("json_schema");
            assertThat(jsonSchema.get("name").asText()).isEqualTo("problem_analysis");
            assertThat(jsonSchema.get("strict").asBoolean()).isTrue();

            JsonNode schema = jsonSchema.get("schema");
            List<String> expected = List.of("analyzable", "subject", "problemType", "keyPoints",
                    "solution", "commonMistakes", "studyTips");
            assertThat(schema.get("type").asText()).isEqualTo("object");
            assertThat(schema.get("additionalProperties").isBoolean()).isTrue();
            assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
            assertThat(fieldNames(schema.get("properties"))).containsExactlyInAnyOrderElementsOf(expected);
            List<String> required = new ArrayList<>();
            schema.get("required").forEach(node -> required.add(node.asText()));
            assertThat(required).as("strict 모드는 모든 필드가 required 여야 한다").containsExactlyInAnyOrderElementsOf(expected);

            JsonNode properties = schema.get("properties");
            assertThat(properties.get("analyzable").get("type").asText()).isEqualTo("boolean");
            assertThat(properties.get("keyPoints").get("type").asText()).isEqualTo("array");
            assertThat(properties.get("keyPoints").get("items").get("type").asText()).isEqualTo("string");
            for (String field : List.of("subject", "problemType", "solution", "commonMistakes", "studyTips")) {
                assertThat(properties.get(field).get("type").asText()).as(field).isEqualTo("string");
            }
        }

        @Test
        @DisplayName("스키마의 7개 필드에 모두 description 이 있고, 필드 뜻을 모델에 알려 준다")
        void describesEverySchemaField() {
            AtomicReference<JsonNode> body = captureRequestAndRespond(chatCompletionJson(analysisJson(), "stop", null));

            openAIClient.analyzeImage(IMAGE_URL);

            JsonNode properties = body.get().get("response_format").get("json_schema").get("schema").get("properties");
            for (String field : List.of("analyzable", "subject", "problemType", "keyPoints",
                    "solution", "commonMistakes", "studyTips")) {
                JsonNode description = properties.get(field).get("description");
                assertThat(description).as(field + " description").isNotNull();
                assertThat(description.isTextual()).as(field).isTrue();
                assertThat(description.asText()).as(field).isNotBlank();
            }
            assertThat(properties.get("subject").get("description").asText()).contains("과목");
            assertThat(properties.get("problemType").get("description").asText())
                    .as("problemType 은 객관식 같은 형식이 아니라 단원이나 개념 영역이다")
                    .contains("형식이 아니라")
                    .contains("단원");
            assertThat(properties.get("keyPoints").get("description").asText()).contains("2~4");
            assertThat(properties.get("solution").get("description").asText())
                    .contains("최종 답")
                    .contains("단계");
            assertThat(properties.get("analyzable").get("description").asText()).contains("false");
            assertThat(properties.get("keyPoints").get("items").get("type").asText())
                    .as("description 을 넣어도 배열 원소 타입은 그대로다")
                    .isEqualTo("string");
        }

        @Test
        @DisplayName("temperature 0.2, max_tokens 3000, 설정된 model 로 보낸다")
        void sendsSamplingParameters() {
            AtomicReference<JsonNode> body = captureRequestAndRespond(chatCompletionJson(analysisJson(), "stop", null));

            openAIClient.analyzeImage(IMAGE_URL);

            assertThat(body.get().get("model").asText()).isEqualTo("gpt-4o");
            assertThat(body.get().get("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.get().get("max_tokens").asInt()).isEqualTo(3000);
            assertThat(nullPaths(body.get(), "$")).as("null 값 키가 본문에 실리면 안 된다").isEmpty();
        }

        @Test
        @DisplayName("이미지는 S3 URL 이 아니라 로더가 만든 data URL 로 들어가고 URL 은 본문에 남지 않는다")
        void sendsDataUrlsInsteadOfS3Urls() throws Exception {
            AtomicReference<JsonNode> body = captureRequestAndRespond(chatCompletionJson(analysisJson(), "stop", null));

            openAIClient.analyzeImages(List.of(IMAGE_URL, IMAGE_URL_2));

            JsonNode userContent = body.get().get("messages").get(1).get("content");
            assertThat(userContent.get(0).get("type").asText()).isEqualTo("text");
            assertThat(userContent.get(0).get("text").asText()).contains("2장");
            assertThat(userContent.get(1).get("type").asText()).isEqualTo("image_url");
            assertThat(userContent.get(1).get("image_url").get("url").asText()).isEqualTo(DATA_URL);
            assertThat(userContent.get(2).get("image_url").get("url").asText()).isEqualTo(DATA_URL_2);
            assertThat(userContent).hasSize(3);

            String raw = objectMapper.writeValueAsString(body.get());
            assertThat(raw).doesNotContain("amazonaws.com").doesNotContain("https://test-ono-bucket");
        }

        @Test
        @DisplayName("시스템 프롬프트에 복습 맥락, 넓힌 대상, 문제 고르기, analyzable, 정답 비노출, 순수 텍스트 원칙이 들어 있다")
        void systemPromptCarriesContext() {
            AtomicReference<JsonNode> body = captureRequestAndRespond(chatCompletionJson(analysisJson(), "stop", null));

            openAIClient.analyzeImage(IMAGE_URL);

            JsonNode system = body.get().get("messages").get(0);
            assertThat(system.get("role").asText()).isEqualTo("system");
            String prompt = system.get("content").asText();
            assertThat(prompt)
                    // 거절을 줄이는 맥락
                    .contains("이미 풀어 보고", "오답노트", "시험을 보고 있는 것이 아니며", "복습용 해설")
                    // 넓힌 대상
                    .contains("수능", "대학 전공", "공무원", "자격증", "NCS", "어학 시험")
                    // 문제 고르기와 판독 불가 응답
                    .contains("가장 크게", "하나의 문제를 나눠 찍은 것", "analyzable 을 false", "거절하지 말고")
                    // 기존 원칙
                    .contains("최종 답을 계산", "LaTeX", "마크다운", "유니코드 기호");
            assertThat(prompt).doesNotContain("중/고등 교육과정");
        }
    }

    @Nested
    @DisplayName("이미지 분석 - 정상 응답")
    class AnalyzeImagesSuccess {

        @Test
        @DisplayName("JSON 응답을 분석 결과로 파싱한다")
        void parsesAnalysisJson() {
            respondWithContent(analysisJson());

            ProblemAnalysisResult result = openAIClient.analyzeImage(IMAGE_URL);

            assertThat(result.getSubject()).isEqualTo("수학");
            assertThat(result.getProblemType()).isEqualTo("이차방정식");
            assertThat(result.getKeyPoints()).containsExactly("근의 공식", "판별식");
            assertThat(result.getSolution()).contains("판별식");
            assertThat(result.getCommonMistakes()).isEqualTo("부호를 놓치기 쉽습니다.");
            assertThat(result.getStudyTips()).isEqualTo("교과서 2단원을 복습하세요.");
            server.verify();
        }

        @Test
        @DisplayName("analyzable 필드가 없는 예전 형식도 분석된 것으로 본다")
        void parsesLegacyJsonWithoutAnalyzable() {
            respondWithContent("{\"subject\":\"영어\",\"problemType\":\"빈칸\",\"keyPoints\":[\"문맥\"],"
                    + "\"solution\":\"s\",\"commonMistakes\":\"c\",\"studyTips\":\"t\"}");

            assertThat(openAIClient.analyzeImage(IMAGE_URL).getSubject()).isEqualTo("영어");
        }

        @Test
        @DisplayName("마크다운 코드블록으로 감싼 응답도 파싱한다")
        void parsesMarkdownFencedJson() {
            respondWithContent("```json\n" + analysisJson() + "\n```");

            ProblemAnalysisResult result = openAIClient.analyzeImage(IMAGE_URL);

            assertThat(result.getSubject()).isEqualTo("수학");
        }

        @Test
        @DisplayName("언어 표기 없는 ``` 코드블록도 파싱한다")
        void parsesPlainFencedJson() {
            respondWithContent("```\n" + analysisJson() + "\n```");

            ProblemAnalysisResult result = openAIClient.analyzeImage(IMAGE_URL);

            assertThat(result.getSubject()).isEqualTo("수학");
        }

        @Test
        @DisplayName("이스케이프가 깨진 LaTeX 응답은 관대한 파서로 복구한다")
        void recoversFromInvalidEscapeSequences() {
            respondWithContent("{\"subject\":\"수학\",\"solution\":\"x = \\( a + b \\) 형태로 두세요.\"}");

            ProblemAnalysisResult result = openAIClient.analyzeImage(IMAGE_URL);

            assertThat(result.getSubject()).isEqualTo("수학");
            assertThat(result.getSolution()).contains("a + b");
        }

        @Test
        @DisplayName("여러 이미지를 한 문제로 묶어 한 번만 호출한다")
        void sendsSingleRequestForMultipleImages() {
            respondWithContent(analysisJson());

            openAIClient.analyzeImages(List.of(IMAGE_URL, IMAGE_URL_2));

            server.verify();
        }

        @Test
        @DisplayName("성공은 success 태그로 계측된다")
        void recordsSuccessMetric() {
            respondWithContent(analysisJson());

            openAIClient.analyzeImage(IMAGE_URL);

            assertThat(externalTimer("analyze_images", "success")).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미지 분석 - 응답 판정 순서")
    class ResponseJudgement {

        @Test
        @DisplayName("message.refusal 이 있으면 REFUSAL (content 가 비어 있어도 재시도하지 않는다)")
        void refusalFieldIsRefusal() {
            respondWithBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                    + "\"refusal\":\"I'm sorry, I can't help with that.\"},\"finish_reason\":\"stop\"}]}");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.REFUSAL);
            assertThat(thrown).hasMessageContaining("거절").hasMessageContaining("can't help");
            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }

        @Test
        @DisplayName("refusal 이 있으면 content 에 정상 JSON 이 있어도 REFUSAL 이 먼저다")
        void refusalWinsOverContent() {
            respondWithBody("{\"choices\":[{\"message\":{\"content\":" + quote(analysisJson())
                    + ",\"refusal\":\"refused\"},\"finish_reason\":\"stop\"}]}");

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.REFUSAL);
        }

        @Test
        @DisplayName("빈 문자열 refusal 은 거절로 보지 않는다")
        void blankRefusalIsIgnored() {
            respondWithBody("{\"choices\":[{\"message\":{\"content\":" + quote(analysisJson())
                    + ",\"refusal\":\"  \"},\"finish_reason\":\"stop\"}]}");

            assertThat(openAIClient.analyzeImage(IMAGE_URL).getSubject()).isEqualTo("수학");
        }

        @Test
        @DisplayName("finish_reason=content_filter 는 REFUSAL")
        void contentFilterIsRefusal() {
            respondWithBody(chatCompletionJson("{\"analyzable\":true,\"subj", "content_filter", null));

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.REFUSAL);
        }

        @Test
        @DisplayName("finish_reason=length 는 같은 요청을 다시 보내도 잘리므로 INVALID_RESPONSE")
        void lengthIsInvalidResponse() {
            respondWithBody(chatCompletionJson("{\"analyzable\":true,\"subject\":\"수학\",\"solution\":\"아주 긴", "length", null));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.INVALID_RESPONSE);
            assertThat(thrown).hasMessageContaining("잘렸");
        }

        @Test
        @DisplayName("analyzable=false 는 UNREADABLE_IMAGE")
        void notAnalyzableIsUnreadable() {
            respondWithContent("{\"analyzable\":false,\"subject\":\"\",\"problemType\":\"\",\"keyPoints\":[],"
                    + "\"solution\":\"\",\"commonMistakes\":\"\",\"studyTips\":\"\"}");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.UNREADABLE_IMAGE);
            assertThat(thrown).hasMessageContaining("분석이 불가능한 이미지");
        }

        @Test
        @DisplayName("content 가 null 이면 NonRetryable 이 아니라 재시도 가능한 실패다")
        void wrapsNullContent() {
            respondWithBody("{\"choices\":[{\"message\":{\"content\":null},\"finish_reason\":\"stop\"}]}");

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .as("응답 자체가 비어 온 것은 모델의 거절이 아니므로 재시도할 수 있어야 한다")
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);

            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }

        @Test
        @DisplayName("choices 가 비어 있으면 재시도 가능한 실패다")
        void wrapsEmptyChoices() {
            respondWithBody("{\"choices\":[]}");

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("JSON 처럼 생겼지만 깨진 응답은 재시도 가능한 파싱 오류로 올린다")
        void wrapsBrokenJson() {
            respondWithContent("{\"subject\": }");

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("응답 파싱");
        }

        private String quote(String value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    @DisplayName("이미지 분석 - JSON 이 아닌 응답 폴백")
    class NonJsonFallback {

        @Test
        @DisplayName("영어 거절 응답은 REFUSAL")
        void detectsEnglishRefusal() {
            respondWithContent("I'm sorry, I can't assist with that request.");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).as("거절 응답을 재시도하면 큐만 계속 돈다").isEqualTo(AnalysisFailureReason.REFUSAL);
        }

        @Test
        @DisplayName("한국어 분석 불가 응답은 UNREADABLE_IMAGE")
        void detectsKoreanNotAnalyzable() {
            respondWithContent("죄송하지만 이미지가 흐려 분석할 수 없습니다.");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.UNREADABLE_IMAGE);
            assertThat(thrown).hasMessageContaining("분석이 불가능한 이미지");
        }

        @Test
        @DisplayName("JSON 이 아닌 잡문 응답은 INVALID_RESPONSE")
        void detectsNonJsonResponse() {
            respondWithContent("이 문제는 어렵네요. 조금 더 고민해보세요.");

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.INVALID_RESPONSE);
        }

        @Test
        @ExtendWith(OutputCaptureExtension.class)
        @DisplayName("JSON 이 아닌 긴 응답은 warn 로그에 앞 200자만 남긴다")
        void logsOnlyPreviewOfNonJsonResponse(CapturedOutput output) {
            String tail = "TAIL_MARKER_SHOULD_NOT_BE_LOGGED";
            respondWithContent("이 문제는 어렵네요. " + "가".repeat(1000) + tail);

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.INVALID_RESPONSE);

            assertThat(output.getAll()).contains("Non-retryable analysis response detected");
            assertThat(output.getAll()).doesNotContain(tail);
        }

        @Test
        @ExtendWith(OutputCaptureExtension.class)
        @DisplayName("깨진 JSON 응답도 error 로그와 스택 트레이스에 원문 전체를 남기지 않는다")
        void logsOnlyPreviewOfBrokenJson(CapturedOutput output) {
            String tail = "TAIL_MARKER_SHOULD_NOT_BE_LOGGED";
            respondWithContent("{\"subject\": \"" + "나".repeat(1000) + tail + "\", }");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(thrown).isNotInstanceOf(NonRetryableAnalysisException.class).hasMessageContaining("응답 파싱");
            assertThat(output.getAll()).contains("Error parsing response");
            assertThat(output.getAll()).doesNotContain(tail);
            Throwable cause = thrown;
            while (cause != null) {
                assertThat(String.valueOf(cause.getMessage())).doesNotContain(tail);
                cause = cause.getCause();
            }
        }

        @Test
        @DisplayName("preview 는 200자까지만 남기고 null 은 그대로 둔다")
        void previewTruncates() {
            assertThat(OpenAIClient.preview(null)).isNull();
            assertThat(OpenAIClient.preview("짧은 응답")).isEqualTo("짧은 응답");
            String longText = "a".repeat(OpenAIClient.LOG_PREVIEW_LENGTH) + "b".repeat(500);
            assertThat(OpenAIClient.preview(longText))
                    .startsWith("a".repeat(OpenAIClient.LOG_PREVIEW_LENGTH))
                    .doesNotContain("b")
                    .hasSizeLessThan(OpenAIClient.LOG_PREVIEW_LENGTH + 20);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "이 사진으로는 문제를 분석할 수 없습니다.",
                "글씨를 인식할 수 없어요.",
                "글자를 읽을 수 없습니다.",
                "내용을 알아볼 수 없습니다.",
                "판독이 어렵습니다.",
                "문제 조건이 명확하지 않습니다.",
                "사진이 흐릿합니다.",
                "이미지가 잘려 있습니다.",
                "다른 이미지를 제공해 주세요.",
                "I'm sorry, the image is too blurry to read.",
                "I am unable to read the text in this image."
        })
        @DisplayName("판독 실패를 직접 말하는 문구는 UNREADABLE_IMAGE")
        void detectsEveryNotAnalyzablePhrase(String content) {
            respondWithContent(content);

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.UNREADABLE_IMAGE);
            assertThat(thrown).hasMessageContaining("분석이 불가능한 이미지");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "I'm sorry, but that is not possible.",
                "I am sorry about this.",
                "I can't assist with that.",
                "I cannot assist with that.",
                "We can't help with this request.",
                "We cannot help with this request.",
                "The model is unable to assist.",
                "The model is unable to help.",
                "Sorry, I won't do that.",
                "As an AI, I cannot do that.",
                "Well, I can't do that.",
                "죄송하지만 도와드릴 수 없습니다.",
                "이 요청에는 도움을 드릴 수 없습니다.",
                "해당 내용은 제공할 수 없습니다."
        })
        @DisplayName("거절 문구는 REFUSAL")
        void detectsEveryRefusalPhrase(String content) {
            respondWithContent(content);

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).as("거절 문구를 놓치면 같은 요청이 계속 재시도된다").isEqualTo(AnalysisFailureReason.REFUSAL);
            assertThat(thrown).hasMessageContaining("거절");
        }

        /**
         * 예전 판독 불가 조건에는 "이미지가", "이미지에 있는 문제", "죄송하지만" 이 있어,
         * 이미지를 언급하기만 한 거절문이 판독 불가로 분류되고 사용자에게 "사진을 다시 찍어라"는 엉뚱한 안내가 나갔다.
         */
        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "이미지가 시험 문제로 보여 도와드릴 수 없습니다.",
                "죄송하지만 이미지에 있는 문제는 도와드릴 수 없습니다."
        })
        @DisplayName("이미지를 언급하기만 한 거절문은 판독 불가가 아니라 REFUSAL")
        void imageMentionDoesNotMeanUnreadable(String content) {
            respondWithContent(content);

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.REFUSAL);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "이미지가 수학 문제입니다. 함수의 극한을 묻고 있습니다.",
                "이미지에 있는 문제를 확인해 주세요."
        })
        @DisplayName("판독 실패도 거절도 아닌 잡문은 INVALID_RESPONSE")
        void imageMentionWithoutFailureIsInvalidResponse(String content) {
            respondWithContent(content);

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.INVALID_RESPONSE);
        }

        @Test
        @DisplayName("여는 중괄호로 시작하지만 닫히지 않은 응답은 JSON 이 아닌 것으로 본다")
        void treatsUnclosedBraceAsNonJson() {
            respondWithContent("{ 이 문제는 함수의 극한을 묻고 있습니다");

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.INVALID_RESPONSE);
            assertThat(thrown).hasMessageContaining("JSON 형식이 아닌");
        }

        @Test
        @DisplayName("실패는 failure 태그로 계측된다")
        void recordsFailureMetric() {
            respondWithContent("I'm sorry, I cannot help with this.");

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(NonRetryableAnalysisException.class);

            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미지 분석 - HTTP 오류")
    class HttpErrors {

        @Test
        @DisplayName("할당량 초과(429 insufficient_quota)는 QUOTA")
        void detectsQuotaExhaustion() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":\"insufficient_quota\"}}"));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.QUOTA);
            assertThat(thrown).hasMessageContaining("할당량");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"invalid_image", "invalid_image_format", "image_parse_error"})
        @DisplayName("이미지 형식 관련 400 은 UNSUPPORTED_IMAGE")
        void imageFormatBadRequestIsUnsupported(String code) {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"You uploaded an unsupported image.\","
                                    + "\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"" + code + "\"}}"));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.UNSUPPORTED_IMAGE);
            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }

        @Test
        @DisplayName("이미지와 무관한 400 은 같은 요청을 다시 보내도 같으므로 재시도하지 않는 REQUEST_REJECTED")
        void otherBadRequestIsRejected() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Invalid schema for response_format\",\"code\":\"invalid_request_error\"}}"));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.REQUEST_REJECTED);
            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }

        @Test
        @DisplayName("본문이 JSON 이 아닌 400 도 REQUEST_REJECTED")
        void badRequestWithNonJsonBodyIsRejected() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("invalid_image"));

            assertThat(reasonOf(catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL))))
                    .isEqualTo(AnalysisFailureReason.REQUEST_REJECTED);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 404, 413, 422})
        @DisplayName("400, 404, 413, 422 는 이미지 코드가 아니어도 재시도하지 않는 REQUEST_REJECTED")
        void rejectedStatusesAreNonRetryable(int status) {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.valueOf(status))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"rejected\",\"code\":null}}"));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.REQUEST_REJECTED);
            assertThat(thrown).hasMessageContaining("status=" + status);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {401, 403, 408, 409})
        @DisplayName("401, 403(키와 권한 설정), 408, 409 는 재시도 대상이다. 소진되면 DLQ 알림으로 운영자가 안다")
        void configurationAndTransientStatusesAreRetryable(int status) {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.valueOf(status))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Incorrect API key provided\",\"code\":\"invalid_api_key\"}}"));

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("AI 이미지 분석 중 오류");
        }

        @Test
        @DisplayName("오류 본문이 길면 예외 메시지에는 앞 200자만 남는다")
        void truncatesLongErrorBody() {
            String tail = "TAIL_MARKER_SHOULD_NOT_APPEAR";
            String body = "{\"error\":{\"message\":\"" + "x".repeat(1000) + tail + "\"}}";
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.REQUEST_REJECTED);
            assertThat(thrown.getMessage()).doesNotContain(tail);
        }

        @Test
        @DisplayName("할당량과 무관한 429 는 재시도 가능한 실패다")
        void wrapsRateLimitWithoutQuotaError() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("서버 오류(500)는 재시도 가능한 실패다")
        void wrapsServerError() {
            server.expect(requestTo(API_URL)).andRespond(withServerError());

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("타임아웃도 재시도 가능한 실패로 감싼다")
        void wrapsTimeout() {
            server.expect(requestTo(API_URL))
                    .andRespond(request -> {
                        throw new ResourceAccessException("read timed out", new SocketTimeoutException("timeout"));
                    });

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미지 분석 - 이미지 로더 실패")
    class LoaderFailure {

        @Test
        @DisplayName("로더가 재시도 불가로 거부하면 OpenAI 를 호출하지 않고 그 이유 그대로 던진다")
        void propagatesNonRetryableWithoutCallingOpenAI() {
            List<String> sixteen = java.util.Collections.nCopies(16, IMAGE_URL);
            when(imageLoader.loadAsDataUrls(sixteen)).thenThrow(
                    new NonRetryableAnalysisException(AnalysisFailureReason.TOO_MANY_IMAGES, "too many"));

            Throwable thrown = catchThrowable(() -> openAIClient.analyzeImages(sixteen));

            assertThat(reasonOf(thrown)).isEqualTo(AnalysisFailureReason.TOO_MANY_IMAGES);
            server.verify(); // 기대한 요청이 없으므로 OpenAI 호출이 없어야 한다
            assertThat(externalTimer("analyze_images", "failure"))
                    .as("OpenAI 를 부르지 않았으니 OpenAI 호출 지표에도 남기지 않는다").isNull();
        }

        @Test
        @DisplayName("로더의 일시적 오류는 재시도 가능한 실패로 그대로 올라간다")
        void propagatesRetryable() {
            when(imageLoader.loadAsDataUrls(anyList())).thenThrow(new RuntimeException("S3 이미지 조회에 실패했습니다."));

            assertThatThrownBy(() -> openAIClient.analyzeImage(IMAGE_URL))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
            server.verify();
        }
    }

    @Nested
    @DisplayName("토큰 메트릭")
    class TokenMetrics {

        @Test
        @DisplayName("분석 응답의 usage 로 입력/출력 토큰을 operation·model·type 태그로 센다")
        void countsAnalysisTokens() {
            respondWithBody(chatCompletionJson(analysisJson(), "stop",
                    "{\"prompt_tokens\":1200,\"completion_tokens\":300,\"total_tokens\":1500}"));
            respondWithBody(chatCompletionJson(analysisJson(), "stop",
                    "{\"prompt_tokens\":800,\"completion_tokens\":100,\"total_tokens\":900}"));

            openAIClient.analyzeImage(IMAGE_URL);
            openAIClient.analyzeImage(IMAGE_URL);

            assertThat(tokenCounter("analyze_images", "input").count()).isEqualTo(2000.0);
            assertThat(tokenCounter("analyze_images", "output").count()).isEqualTo(400.0);
        }

        @Test
        @DisplayName("응답이 거절이어도 쓴 토큰은 센다")
        void countsTokensEvenWhenRefused() {
            respondWithBody("{\"choices\":[{\"message\":{\"content\":null,\"refusal\":\"no\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":900,\"completion_tokens\":5,\"total_tokens\":905}}");

            catchThrowable(() -> openAIClient.analyzeImage(IMAGE_URL));

            assertThat(tokenCounter("analyze_images", "input").count()).isEqualTo(900.0);
            assertThat(tokenCounter("analyze_images", "output").count()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("usage 가 없으면 토큰 메트릭을 만들지 않는다")
        void skipsWhenUsageMissing() {
            respondWithContent(analysisJson());

            openAIClient.analyzeImage(IMAGE_URL);

            assertThat(meterRegistry.find("ono.ai.tokens").counters()).isEmpty();
        }

        @Test
        @DisplayName("학습 리포트 토큰은 recommend_learning_report 로 따로 센다")
        void countsLearningReportTokens() {
            respondWithBody(chatCompletionJson("{\"strengths\":[\"좋습니다\"]}", "stop",
                    "{\"prompt_tokens\":400,\"completion_tokens\":120,\"total_tokens\":520}"));

            openAIClient.recommendLearningReport(Map.of("solveCount", 1));

            assertThat(tokenCounter("recommend_learning_report", "input").count()).isEqualTo(400.0);
            assertThat(tokenCounter("recommend_learning_report", "output").count()).isEqualTo(120.0);
            assertThat(tokenCounter("analyze_images", "input")).isNull();
        }
    }

    @Nested
    @DisplayName("학습 리포트 추천")
    class LearningReportRecommendation {

        @Test
        @DisplayName("요청 본문은 예전 그대로다: response_format 키가 없고 null 값 키도 없다")
        void requestBodyIsUnchanged() {
            AtomicReference<JsonNode> body = captureRequestAndRespond(
                    chatCompletionJson("{\"strengths\":[\"좋습니다\"]}", "stop", null));

            openAIClient.recommendLearningReport(Map.of("solveCount", 12));

            JsonNode request = body.get();
            assertThat(fieldNames(request))
                    .as("학습 리포트 요청이 깨지면 룰 기반으로 조용히 폴백되어 장애가 보이지 않는다")
                    .containsExactlyInAnyOrder("model", "messages", "max_tokens", "temperature");
            assertThat(request.has("response_format")).isFalse();
            assertThat(nullPaths(request, "$")).isEmpty();
            assertThat(request.get("model").asText()).isEqualTo("gpt-4o");
            assertThat(request.get("temperature").asDouble()).isEqualTo(0.3);
            assertThat(request.get("max_tokens").asInt()).isEqualTo(700);

            JsonNode messages = request.get("messages");
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
            assertThat(messages.get(0).get("content").isTextual()).isTrue();
            assertThat(messages.get(0).get("content").asText()).contains("학습 리포트 코치");
            assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
            assertThat(messages.get(1).get("content").asText()).isEqualTo("학습 요약 데이터: {\"solveCount\":12}");
        }

        @Test
        @DisplayName("추천 JSON 을 파싱한다")
        void parsesRecommendation() {
            respondWithContent("""
                    {
                      "strengths": ["복습 주기가 안정적입니다"],
                      "gaps": ["오답 재도전이 부족합니다"],
                      "actions": ["매일 3문제 복습", "주말 오답 정리"],
                      "nextWeekGoal": "다음 주 복습 15회를 목표로 하세요",
                      "confidence": 82
                    }
                    """);

            Optional<LearningRecommendations> result =
                    openAIClient.recommendLearningReport(Map.of("solveCount", 12));

            assertThat(result).isPresent();
            assertThat(result.get().strengths()).containsExactly("복습 주기가 안정적입니다");
            assertThat(result.get().actions()).hasSize(2);
            assertThat(result.get().confidence()).isEqualTo(82.0);
            assertThat(externalTimer("recommend_learning_report", "success")).isNotNull();
        }

        @Test
        @DisplayName("일부 필드가 빠져도 기본값으로 채운다")
        void fillsDefaultsForMissingFields() {
            respondWithContent("{\"strengths\": [\"좋습니다\"]}");

            Optional<LearningRecommendations> result =
                    openAIClient.recommendLearningReport(Map.of("solveCount", 1));

            assertThat(result).isPresent();
            assertThat(result.get().gaps()).isEmpty();
            assertThat(result.get().nextWeekGoal()).isNotBlank();
            assertThat(result.get().confidence()).isEqualTo(70.0);
        }

        @Test
        @DisplayName("AI 비활성화 설정이면 호출조차 하지 않는다")
        void skipsCallWhenDisabled() {
            ReflectionTestUtils.setField(openAIClient, "learningReportAiEnabled", false);

            assertThat(openAIClient.recommendLearningReport(Map.of("solveCount", 1))).isEmpty();

            server.verify(); // 기대한 요청이 없으므로 아무 호출도 없어야 한다
        }

        @Test
        @DisplayName("호출이 실패하면 예외 대신 빈 값으로 폴백한다")
        void fallsBackOnHttpFailure() {
            server.expect(requestTo(API_URL)).andRespond(withServerError());

            assertThat(openAIClient.recommendLearningReport(Map.of("solveCount", 1)))
                    .as("추천 실패가 리포트 생성 전체를 막으면 안 된다")
                    .isEmpty();
            assertThat(externalTimer("recommend_learning_report", "failure")).isNotNull();
        }

        @Test
        @DisplayName("깨진 JSON 응답도 빈 값으로 폴백한다")
        void fallsBackOnBrokenJson() {
            respondWithContent("이번 주도 수고했어요!");

            assertThat(openAIClient.recommendLearningReport(Map.of("solveCount", 1))).isEmpty();
        }
    }
}
