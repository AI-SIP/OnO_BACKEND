package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
import com.aisip.OnO.backend.util.ai.dto.*;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class OpenAIClient {

    static final String OPERATION_ANALYZE_IMAGES = "analyze_images";
    static final String OPERATION_LEARNING_REPORT = "recommend_learning_report";

    /** 이미지 자체를 OpenAI 가 읽지 못했다는 400 오류 코드. 같은 이미지로 재시도해도 같은 결과다 */
    private static final Set<String> IMAGE_FORMAT_ERROR_CODES = Set.of(
            "invalid_image", "invalid_image_format", "image_parse_error", "unsupported_image_media_type");

    private static final Map<String, Object> ANALYSIS_RESPONSE_FORMAT = createAnalysisResponseFormat();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AnalysisImageLoader analysisImageLoader;
    private static final ObjectMapper LENIENT_OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .build();

    @Value("${openai.model}")
    private String model;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${learning-report.ai.enabled:true}")
    private boolean learningReportAiEnabled;

    /**
     * lombok.config 가 없어 @RequiredArgsConstructor 로는 @Qualifier 가 생성자 파라미터로 복사되지 않는다.
     * 그러면 타임아웃 없는 범용 restTemplate 이 주입되므로 생성자를 직접 쓴다.
     */
    public OpenAIClient(
            @Qualifier(OpenAIHttpClientConfig.AI_REST_TEMPLATE) RestTemplate restTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AnalysisImageLoader analysisImageLoader
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.analysisImageLoader = analysisImageLoader;
    }

    /**
     * 여러 이미지 URL을 분석하여 문제에 대한 정보를 추출합니다.
     * 이미지는 서버가 S3 에서 직접 읽어 data URL 로 보낸다. OpenAI 가 URL 을 내려받다 타임아웃 나는 일을 없애기 위해서다.
     */
    public ProblemAnalysisResult analyzeImages(List<String> imageUrls) {
        log.info("Starting image analysis for {} images", imageUrls == null ? 0 : imageUrls.size());

        // 1. 이미지 로드. 외부 URL·장수 초과·형식 오류는 여기서 NonRetryable 로 끝난다(OpenAI 호출 시간 계측에서 뺀다)
        List<String> imageDataUrls = analysisImageLoader.loadAsDataUrls(imageUrls);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 2. Vision API 요청 메시지 생성
            List<Message> messages = createVisionMessages(imageDataUrls);

            // 3. ChatGPT API 요청 생성. 응답은 Structured Outputs 스키마로 강제한다
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(3000)
                    .temperature(0.2)
                    .responseFormat(ANALYSIS_RESPONSE_FORMAT)
                    .build();

            // 4. API 호출
            ResponseEntity<ChatCompletionResponse> response = restTemplate.postForEntity(
                    apiUrl,
                    new HttpEntity<>(request, jsonHeaders()),
                    ChatCompletionResponse.class
            );

            ChatCompletionResponse body = response.getBody();
            recordTokenUsage(OPERATION_ANALYZE_IMAGES, body);

            // 5. 응답 판정과 파싱
            ProblemAnalysisResult result = interpretAnalysisResponse(body);
            recordExternalCall("openai", OPERATION_ANALYZE_IMAGES, "success", sample);
            return result;

        } catch (HttpClientErrorException e) {
            recordExternalCall("openai", OPERATION_ANALYZE_IMAGES, "failure", sample);
            String responseBody = e.getResponseBodyAsString();
            if (isNonRetryableQuotaError(e, responseBody)) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.QUOTA, "OpenAI 할당량이 초과되어 분석을 진행할 수 없습니다.", e);
            }
            if (isImageFormatError(e, responseBody)) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.UNSUPPORTED_IMAGE,
                        "OpenAI 가 이미지를 읽지 못했습니다: " + responseBody, e);
            }
            log.error("Error analyzing images: {}", e.getMessage(), e);
            throw new RuntimeException("AI 이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        } catch (NonRetryableAnalysisException e) {
            recordExternalCall("openai", OPERATION_ANALYZE_IMAGES, "failure", sample);
            log.warn("Non-retryable image analysis failure - reason: {}, message: {}", e.getReason(), e.getMessage());
            throw e;
        } catch (Exception e) {
            recordExternalCall("openai", OPERATION_ANALYZE_IMAGES, "failure", sample);
            log.error("Error analyzing images: {}", e.getMessage(), e);
            throw new RuntimeException("AI 이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 단일 이미지 분석 (하위 호환성)
     */
    public ProblemAnalysisResult analyzeImage(String imageUrl) {
        return analyzeImages(List.of(imageUrl));
    }

    public Optional<LearningRecommendations> recommendLearningReport(Map<String, Object> summaryPayload) {
        if (!learningReportAiEnabled) {
            return Optional.empty();
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String summaryJson = objectMapper.writeValueAsString(summaryPayload);
            List<Message> messages = List.of(
                    Message.builder()
                            .role("system")
                            .content(createLearningReportSystemPrompt())
                            .build(),
                    Message.builder()
                            .role("user")
                            .content("학습 요약 데이터: " + summaryJson)
                            .build()
            );

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(700)
                    .temperature(0.3)
                    .build();

            ResponseEntity<ChatCompletionResponse> response = restTemplate.postForEntity(
                    apiUrl,
                    new HttpEntity<>(request, jsonHeaders()),
                    ChatCompletionResponse.class
            );

            ChatCompletionResponse body = response.getBody();
            recordTokenUsage(OPERATION_LEARNING_REPORT, body);

            String content = body
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            recordExternalCall("openai", OPERATION_LEARNING_REPORT, "success", sample);
            return parseLearningRecommendation(content);
        } catch (Exception e) {
            recordExternalCall("openai", OPERATION_LEARNING_REPORT, "failure", sample);
            log.warn("AI learning recommendation failed, fallback to rule-based: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private void recordExternalCall(String dependency, String operation, String outcome, Timer.Sample sample) {
        sample.stop(
                Timer.builder("ono.external.requests")
                        .description("External dependency call latency")
                        .publishPercentileHistogram()
                        .tag("dependency", dependency)
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(meterRegistry)
        );
    }

    /**
     * 응답의 usage 로 입력/출력 토큰 수를 센다. usage 가 없으면 건너뛴다.
     */
    private void recordTokenUsage(String operation, ChatCompletionResponse body) {
        if (body == null || body.getUsage() == null) {
            return;
        }
        incrementTokens(operation, "input", body.getUsage().getPromptTokens());
        incrementTokens(operation, "output", body.getUsage().getCompletionTokens());
    }

    private void incrementTokens(String operation, String type, Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        Counter.builder("ono.ai.tokens")
                .description("OpenAI token usage")
                .tag("operation", operation)
                .tag("model", model == null ? "unknown" : model)
                .tag("type", type)
                .register(meterRegistry)
                .increment(tokens);
    }

    /**
     * 판정 순서: 거절 → 길이 초과 → 빈 응답 → JSON(analyzable) → JSON 이 아닌 응답 휴리스틱.
     */
    private ProblemAnalysisResult interpretAnalysisResponse(ChatCompletionResponse body) {
        ChatCompletionResponse.Choice choice = (body == null || body.getChoices() == null || body.getChoices().isEmpty())
                ? null
                : body.getChoices().get(0);
        ChatCompletionResponse.MessageResponse message = choice == null ? null : choice.getMessage();

        if (choice != null) {
            String refusal = message == null ? null : message.getRefusal();
            if (refusal != null && !refusal.isBlank()) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.REFUSAL,
                        "AI가 요청을 거절하여 분석을 진행할 수 없습니다: " + refusal);
            }
            if ("content_filter".equals(choice.getFinishReason())) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.REFUSAL,
                        "AI가 요청을 거절하여 분석을 진행할 수 없습니다: finish_reason=content_filter");
            }
            // 같은 요청을 다시 보내도 같은 지점에서 잘린다
            if ("length".equals(choice.getFinishReason())) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_RESPONSE,
                        "AI 응답이 최대 토큰 수에서 잘렸습니다.");
            }
        }

        String content = message == null ? null : message.getContent();
        if (content == null) {
            // 응답 자체가 비어 온 것은 모델의 거절이 아니므로 재시도할 수 있게 둔다
            throw new IllegalStateException("AI 응답에 content 가 없습니다.");
        }
        log.debug("Received response from OpenAI - contentLength: {}", content.length());
        return parseResponse(content);
    }

    /**
     * Vision API용 메시지 생성
     */
    private List<Message> createVisionMessages(List<String> imageDataUrls) {
        List<Message> messages = new ArrayList<>();

        // System 메시지
        messages.add(Message.builder()
                .role("system")
                .content(createSystemPrompt())
                .build());

        // User 메시지 (텍스트 + 이미지들)
        List<ContentPart> contentParts = new ArrayList<>();

        // 텍스트 파트
        String textPrompt = createTextPrompt(imageDataUrls.size());
        contentParts.add(ContentPart.builder()
                .type("text")
                .text(textPrompt)
                .build());

        // 이미지 파트들
        for (String imageDataUrl : imageDataUrls) {
            contentParts.add(ContentPart.builder()
                    .type("image_url")
                    .imageUrl(ImageUrl.builder().url(imageDataUrl).build())
                    .build());
        }

        messages.add(Message.builder()
                .role("user")
                .content(contentParts)
                .build());

        return messages;
    }

    /**
     * 시험지 사진을 "시험 부정행위"로 오해해 거절하는 일이 많아(운영 실패 표본 9건 중 6건),
     * 자료의 출처와 목적(본인이 이미 푼 문제의 복습)을 먼저 밝힌다.
     */
    String createSystemPrompt() {
        return """
            당신은 학습자가 스스로 문제를 다시 풀 수 있도록 사고의 물꼬를 터주는 '복습 길잡이'입니다.

            [자료의 맥락]
            - 이 이미지는 학습자가 이미 풀어 보고 틀렸거나 헷갈렸던 문제를, 복습하려고 자신의 오답노트 앱에 직접 올린 것입니다.
            - 학습자는 지금 시험을 보고 있는 것이 아니며, 이 분석은 시험이 끝난 뒤 개념을 다시 익히기 위한 복습용 해설입니다.
            - 대상은 모든 학습용 문제입니다. 초중고 교과, 수능, 대학 전공, 공무원·자격증·NCS 시험, 토익·SAT 같은 어학 시험 등 어떤 과목이든 분석하세요.
            - 용어는 해당 시험과 과목에서 실제로 쓰는 표준 용어를 사용하세요.

            [분석 대상 고르기]
            - 한 이미지에 문제가 여러 개 있으면, 가장 크게 보이거나 화면 중심에 있는 문제 하나를 골라 그 문제만 분석하세요.
            - 이미지가 여러 장이면 하나의 문제를 나눠 찍은 것으로 보고 종합해서 하나의 문제로 분석하세요.
            - 이미지에서 학습용 문제를 찾을 수 없으면(일반 사진, 빈 화면, 글자를 알아볼 수 없는 경우 등) 거절하지 말고
              analyzable 을 false 로 하고 나머지 문자열은 빈 문자열, keyPoints 는 빈 배열로 응답하세요.

            [분석 및 작성 원칙]
            1. Solve No-More: 수치를 대입해 최종 답을 계산하거나 정답 선지를 알려주지 마세요. 대신 "A 공식을 사용해 B를 구한다"와 같은 '방법론'을 제시하세요.
            2. Thinking Step (solution):
               - 1단계: 문제에서 주어진 조건(Knowns)과 구해야 하는 것(Unknowns)을 명확히 정의하세요.
               - 2단계: 이 문제를 풀기 위해 머릿속에서 꺼내야 할 핵심 공식, 정의, 법리, 문법 규칙 등을 언급하세요.
               - 3단계: 어떤 순서로 접근해야 하는지 '전략'을 설명하세요.
            3. Error Analysis (commonMistakes): 계산 실수보다는 '개념의 오용'이나 '조건을 잘못 읽는 경우' 등 사고의 오류를 짚어주세요.
            4. keyPoints 에는 이 문제에 필요한 핵심 개념이나 공식을 2~4개 적으세요.

            [작성 형식]
            - 모든 값은 순수한 텍스트로 작성하세요. LaTeX, 마크다운, 이스케이프 문자를 쓰지 마세요.
            - 수식은 한국어 문장으로 풀어 쓰고, 필요하면 ², ³, √, π, ≤, ≥ 같은 유니코드 기호까지만 쓰세요.
            - 모든 내용은 한국어로 작성하며, 학습자가 스스로 답을 찾을 수 있게 격려하는 톤을 유지하세요.
            - 응답은 지정된 JSON 스키마(analyzable, subject, problemType, keyPoints, solution, commonMistakes, studyTips)를 따르세요.
            """;
    }

    private String createTextPrompt(int imageCount) {
        if (imageCount == 1) {
            return "이미지에 있는 문제를 분석하여 지정된 JSON 형식으로 복습용 분석 결과를 제공해주세요.";
        } else {
            return String.format(
                    "다음 %d장의 이미지는 하나의 문제를 여러 장으로 나눈 것입니다. " +
                    "모든 이미지를 종합하여 하나의 문제로 분석하고, " +
                    "지정된 JSON 형식으로 복습용 분석 결과를 제공해주세요.",
                    imageCount
            );
        }
    }

    /**
     * Structured Outputs(strict) 스키마. strict 모드는 모든 필드가 required 이고 additionalProperties 가 false 여야 한다.
     * 앱이 그리는 필드 이름(ProblemAnalysisResult)은 그대로 두고 analyzable 만 더한다.
     */
    private static Map<String, Object> createAnalysisResponseFormat() {
        Map<String, Object> stringType = Map.of("type", "string");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("analyzable", Map.of("type", "boolean"));
        properties.put("subject", stringType);
        properties.put("problemType", stringType);
        properties.put("keyPoints", Map.of("type", "array", "items", stringType));
        properties.put("solution", stringType);
        properties.put("commonMistakes", stringType);
        properties.put("studyTips", stringType);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "problem_analysis");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return java.util.Collections.unmodifiableMap(responseFormat);
    }

    private String createLearningReportSystemPrompt() {
        return """
                너는 학습 리포트 코치다.
                입력으로 받은 요약 지표를 바탕으로 학습 피드백을 JSON으로만 반환해라.
                반드시 아래 스키마를 지켜라.
                {
                  "strengths": ["문장", "문장"],
                  "gaps": ["문장", "문장"],
                  "actions": ["실행 가능한 액션", "실행 가능한 액션", "실행 가능한 액션"],
                  "nextWeekGoal": "수치가 포함된 1문장 목표",
                  "confidence": 0~100 사이 숫자
                }
                한국어로 작성하고, 각 배열은 2~3개로 제한해라.
                JSON 외 텍스트를 출력하지 마라.
                """;
    }

    private ProblemAnalysisResult parseResponse(String response) {
        try {
            // JSON 형식이 아닌 경우 처리
            String jsonContent = extractJsonFromResponse(response);

            if (!looksLikeJsonObject(jsonContent)) {
                if (isImageNotAnalyzableResponse(jsonContent)) {
                    throw new NonRetryableAnalysisException(AnalysisFailureReason.UNREADABLE_IMAGE, "분석이 불가능한 이미지입니다.");
                }
                if (isRefusalResponse(jsonContent)) {
                    throw new NonRetryableAnalysisException(AnalysisFailureReason.REFUSAL, "AI가 요청을 거절하여 분석을 진행할 수 없습니다.");
                }
                throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_RESPONSE, "AI가 JSON 형식이 아닌 응답을 반환했습니다.");
            }

            Map<String, Object> parsed = readJsonMap(jsonContent);

            // 스키마가 analyzable=false 로 답하면 문제를 찾지 못한 이미지다. 필드가 없으면(구 형식) 분석된 것으로 본다
            if (Boolean.FALSE.equals(parsed.get("analyzable"))) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.UNREADABLE_IMAGE, "분석이 불가능한 이미지입니다.");
            }

            return ProblemAnalysisResult.builder()
                    .subject((String) parsed.get("subject"))
                    .problemType((String) parsed.get("problemType"))
                    .keyPoints((List<String>) parsed.get("keyPoints"))
                    .solution((String) parsed.get("solution"))
                    .commonMistakes((String) parsed.get("commonMistakes"))
                    .studyTips((String) parsed.get("studyTips"))
                    .build();

        } catch (Exception e) {
            if (e instanceof NonRetryableAnalysisException nonRetryableAnalysisException) {
                log.warn("Non-retryable analysis response detected: {}", response);
                throw nonRetryableAnalysisException;
            }
            log.error("Error parsing response: {}", response, e);
            throw new RuntimeException("AI 분석 결과 응답 파싱 중 오류가 발생했습니다.", e);
        }
    }

    private String extractJsonFromResponse(String response) {
        // Markdown 코드 블록 제거
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }

    private Optional<LearningRecommendations> parseLearningRecommendation(String response) {
        try {
            String jsonContent = extractJsonFromResponse(response);
            JsonNode root = objectMapper.readTree(jsonContent);

            LearningRecommendations recommendations = LearningRecommendations.builder()
                    .strengths(readStringArray(root, "strengths"))
                    .gaps(readStringArray(root, "gaps"))
                    .actions(readStringArray(root, "actions"))
                    .nextWeekGoal(readString(root, "nextWeekGoal", "다음 주 목표를 설정해 3회 이상 복습해보세요."))
                    .confidence(readDouble(root, "confidence", 70.0))
                    .build();

            return Optional.of(recommendations);
        } catch (Exception e) {
            log.warn("AI learning recommendation parsing failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> readStringArray(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .toList();
    }

    private String readString(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : defaultValue;
    }

    private Double readDouble(JsonNode root, String field, Double defaultValue) {
        JsonNode node = root.path(field);
        return node.isNumber() ? node.asDouble() : defaultValue;
    }

    private boolean looksLikeJsonObject(String content) {
        return content.startsWith("{") && content.endsWith("}");
    }

    /**
     * JSON 이 아닌 응답 중 "이미지를 읽지 못했다"는 뜻의 문구.
     * 예전에는 "이미지가", "죄송하지만" 처럼 넓은 조건이 있어, 이미지를 언급하는 거절문까지 판독 불가로 분류됐다.
     * 판독 실패를 직접 말하는 표현만 남긴다.
     */
    private boolean isImageNotAnalyzableResponse(String content) {
        String lower = content.toLowerCase();
        return content.contains("분석할 수 없")
                || content.contains("인식할 수 없")
                || content.contains("읽을 수 없")
                || content.contains("알아볼 수 없")
                || content.contains("판독")
                || content.contains("명확하지 않")
                || content.contains("흐려")
                || content.contains("흐릿")
                || content.contains("잘려")
                || content.contains("다른 이미지를 제공")
                || lower.contains("unable to read")
                || lower.contains("can't read")
                || lower.contains("cannot read")
                || lower.contains("too blurry")
                || lower.contains("not legible")
                || lower.contains("illegible");
    }

    private boolean isRefusalResponse(String content) {
        String lower = content.toLowerCase();
        return content.contains("도와드릴 수 없")
                || content.contains("도와 드릴 수 없")
                || content.contains("도움을 드릴 수 없")
                || content.contains("제공할 수 없")
                || content.contains("답변할 수 없")
                || lower.contains("i'm sorry")
                || lower.contains("i am sorry")
                || lower.contains("can't assist")
                || lower.contains("cannot assist")
                || lower.contains("can't help")
                || lower.contains("cannot help")
                || lower.contains("unable to assist")
                || lower.contains("unable to help")
                || lower.contains("sorry, i")
                || lower.contains("i cannot")
                || lower.contains("i can't");
    }

    private boolean isNonRetryableQuotaError(HttpClientErrorException e, String responseBody) {
        return e.getStatusCode().value() == 429 && responseBody != null && responseBody.contains("insufficient_quota");
    }

    /**
     * 400 중 이미지 형식 자체를 거부한 경우. error.code 로 판정하고, 본문이 JSON 이 아니면 재시도 대상으로 둔다.
     */
    private boolean isImageFormatError(HttpClientErrorException e, String responseBody) {
        if (e.getStatusCode().value() != 400 || responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            JsonNode code = objectMapper.readTree(responseBody).path("error").path("code");
            return code.isTextual() && IMAGE_FORMAT_ERROR_CODES.contains(code.asText());
        } catch (Exception parseException) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String jsonContent) throws Exception {
        try {
            return objectMapper.readValue(jsonContent, Map.class);
        } catch (Exception strictParseException) {
            log.warn("Strict JSON parse failed, retrying with lenient parser: {}", strictParseException.getMessage());
            return LENIENT_OBJECT_MAPPER.readValue(jsonContent, Map.class);
        }
    }
}
