package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * 프로덕션에서 실제로 관측된 응답(거절 문구, 이미지 URL 타임아웃 400, 할당량 초과 429)을 그대로 재현한다.
 */
@DisplayName("OpenAI 클라이언트")
class OpenAIClientTest {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MeterRegistry meterRegistry;
    private OpenAIClient openAIClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        meterRegistry = new SimpleMeterRegistry();
        openAIClient = new OpenAIClient(restTemplate, new ObjectMapper(), meterRegistry);
        ReflectionTestUtils.setField(openAIClient, "model", "gpt-4o");
        ReflectionTestUtils.setField(openAIClient, "apiKey", "test-openai-api-key");
        ReflectionTestUtils.setField(openAIClient, "apiUrl", API_URL);
        ReflectionTestUtils.setField(openAIClient, "learningReportAiEnabled", true);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    private void respondWithContent(String content) {
        server.expect(requestTo(API_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-openai-api-key"))
                .andRespond(withSuccess(chatCompletionJson(content), MediaType.APPLICATION_JSON));
    }

    private String chatCompletionJson(String content) {
        try {
            String escaped = new ObjectMapper().writeValueAsString(content);
            return "{\"choices\":[{\"message\":{\"content\":" + escaped + "}}]}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String analysisJson() {
        return """
                {
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

    @Nested
    @DisplayName("이미지 분석 - 정상 응답")
    class AnalyzeImagesSuccess {

        @Test
        @DisplayName("JSON 응답을 분석 결과로 파싱한다")
        void parsesAnalysisJson() {
            respondWithContent(analysisJson());

            ProblemAnalysisResult result = openAIClient.analyzeImage("https://cdn.test/image.jpg");

            assertThat(result.getSubject()).isEqualTo("수학");
            assertThat(result.getProblemType()).isEqualTo("이차방정식");
            assertThat(result.getKeyPoints()).containsExactly("근의 공식", "판별식");
            assertThat(result.getSolution()).contains("판별식");
            server.verify();
        }

        @Test
        @DisplayName("마크다운 코드블록으로 감싼 응답도 파싱한다")
        void parsesMarkdownFencedJson() {
            respondWithContent("```json\n" + analysisJson() + "\n```");

            ProblemAnalysisResult result = openAIClient.analyzeImage("https://cdn.test/image.jpg");

            assertThat(result.getSubject()).isEqualTo("수학");
        }

        @Test
        @DisplayName("언어 표기 없는 ``` 코드블록도 파싱한다")
        void parsesPlainFencedJson() {
            respondWithContent("```\n" + analysisJson() + "\n```");

            ProblemAnalysisResult result = openAIClient.analyzeImage("https://cdn.test/image.jpg");

            assertThat(result.getSubject()).isEqualTo("수학");
        }

        @Test
        @DisplayName("이스케이프가 깨진 LaTeX 응답은 관대한 파서로 복구한다")
        void recoversFromInvalidEscapeSequences() {
            respondWithContent("{\"subject\":\"수학\",\"solution\":\"x = \\( a + b \\) 형태로 두세요.\"}");

            ProblemAnalysisResult result = openAIClient.analyzeImage("https://cdn.test/image.jpg");

            assertThat(result.getSubject()).isEqualTo("수학");
            assertThat(result.getSolution()).contains("a + b");
        }

        @Test
        @DisplayName("여러 이미지를 한 문제로 묶어 한 번만 호출한다")
        void sendsSingleRequestForMultipleImages() {
            respondWithContent(analysisJson());

            openAIClient.analyzeImages(List.of("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"));

            server.verify();
        }

        @Test
        @DisplayName("성공은 success 태그로 계측된다")
        void recordsSuccessMetric() {
            respondWithContent(analysisJson());

            openAIClient.analyzeImage("https://cdn.test/image.jpg");

            assertThat(externalTimer("analyze_images", "success")).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미지 분석 - 재시도해도 소용없는 실패")
    class NonRetryableFailure {

        @Test
        @DisplayName("영어 거절 응답은 NonRetryableAnalysisException 이다")
        void detectsEnglishRefusal() {
            respondWithContent("I'm sorry, I can't assist with that request.");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .as("거절 응답을 재시도하면 큐만 계속 돈다")
                    .isInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("한국어 분석 불가 응답도 NonRetryableAnalysisException 이다")
        void detectsKoreanNotAnalyzable() {
            respondWithContent("죄송하지만 이미지가 흐려 분석할 수 없습니다.");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("분석이 불가능한 이미지");
        }

        @Test
        @DisplayName("JSON 이 아닌 잡문 응답도 재시도 대상이 아니다")
        void detectsNonJsonResponse() {
            respondWithContent("이 문제는 어렵네요. 조금 더 고민해보세요.");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("할당량 초과(429 insufficient_quota)는 재시도 대상이 아니다")
        void detectsQuotaExhaustion() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":\"insufficient_quota\"}}"));

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("할당량");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "이 사진으로는 문제를 분석할 수 없습니다.",
                "글씨를 인식할 수 없어요.",
                "문제 조건이 명확하지 않습니다.",
                "이미지가 잘려 있습니다.",
                "이미지에 있는 문제를 확인해 주세요.",
                "죄송하지만 도와드릴 수 없습니다.",
                "다른 이미지를 제공해 주세요."
        })
        @DisplayName("한국어 분석 불가 문구는 전부 재시도 대상에서 제외한다")
        void detectsEveryKoreanNotAnalyzablePhrase(String content) {
            respondWithContent(content);

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .as("이 문구를 놓치면 분석 불가 이미지가 큐에서 무한 재시도된다")
                    .isInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("분석이 불가능한 이미지");
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
                "Well, I can't do that."
        })
        @DisplayName("영어 거절 문구는 전부 재시도 대상에서 제외한다")
        void detectsEveryEnglishRefusalPhrase(String content) {
            respondWithContent(content);

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .as("거절 문구를 놓치면 같은 요청이 계속 재시도된다")
                    .isInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("거절");
        }

        @Test
        @DisplayName("여는 중괄호로 시작하지만 닫히지 않은 응답은 JSON 이 아닌 것으로 본다")
        void treatsUnclosedBraceAsNonJson() {
            respondWithContent("{ 이 문제는 함수의 극한을 묻고 있습니다");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("JSON 형식이 아닌");
        }

        @Test
        @DisplayName("실패는 failure 태그로 계측된다")
        void recordsFailureMetric() {
            respondWithContent("I'm sorry, I cannot help with this.");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(NonRetryableAnalysisException.class);

            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미지 분석 - 재시도 가능한 실패")
    class RetryableFailure {

        @Test
        @DisplayName("이미지 URL 을 읽지 못한 400 은 일반 RuntimeException 으로 올린다")
        void wrapsBadRequest() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Timeout while downloading image\"}}"));

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class)
                    .hasMessageContaining("AI 이미지 분석 중 오류");
        }

        @Test
        @DisplayName("할당량과 무관한 429 는 재시도 가능한 실패다")
        void wrapsRateLimitWithoutQuotaError() {
            server.expect(requestTo(API_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("서버 오류(500)는 재시도 가능한 실패다")
        void wrapsServerError() {
            server.expect(requestTo(API_URL)).andRespond(withServerError());

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
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

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("JSON 처럼 생겼지만 깨진 응답은 파싱 오류로 올린다")
        void wrapsBrokenJson() {
            respondWithContent("{\"subject\": }");

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("응답 파싱");
        }

        @Test
        @DisplayName("choices 가 비어 있으면 실패로 처리한다")
        void wrapsEmptyChoices() {
            server.expect(requestTo(API_URL))
                    .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("content 가 null 이면 NonRetryable 이 아니라 재시도 가능한 실패다")
        void wrapsNullContent() {
            server.expect(requestTo(API_URL))
                    .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":null}}]}",
                            MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> openAIClient.analyzeImage("https://cdn.test/image.jpg"))
                    .as("응답 자체가 비어 온 것은 모델의 거절이 아니므로 재시도할 수 있어야 한다")
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);

            assertThat(externalTimer("analyze_images", "failure")).isNotNull();
        }
    }

    @Nested
    @DisplayName("학습 리포트 추천")
    class LearningReportRecommendation {

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
