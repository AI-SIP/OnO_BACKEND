package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.util.ai.dto.*;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final ObjectMapper LENIENT_OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .build();

    @Value("${openai.model}")
    private String model;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    /**
     * 여러 이미지 URL을 분석하여 문제에 대한 정보를 추출합니다.
     */
    public ProblemAnalysisResult analyzeImages(List<String> imageUrls) {
        try {
            log.info("Starting image analysis for {} images", imageUrls.size());

            // 1. Vision API 요청 메시지 생성
            List<Message> messages = createVisionMessages(imageUrls);

            // 2. ChatGPT API 요청 생성
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(2000)
                    .temperature(0.7)
                    .build();

            // 3. HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

            // 4. API 호출
            ResponseEntity<ChatCompletionResponse> response = restTemplate.postForEntity(
                    apiUrl,
                    entity,
                    ChatCompletionResponse.class
            );

            // 5. 응답 추출
            String content = response.getBody()
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            log.info("Received response from OpenAI: {}", content);

            // 6. JSON 응답 파싱
            return parseResponse(content);

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (isNonRetryableQuotaError(e, responseBody)) {
                throw new NonRetryableAnalysisException("OpenAI 할당량이 초과되어 분석을 진행할 수 없습니다.", e);
            }
            log.error("Error analyzing images: {}", e.getMessage(), e);
            throw new RuntimeException("AI 이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        } catch (NonRetryableAnalysisException e) {
            log.warn("Non-retryable image analysis failure: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
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

    /**
     * Vision API용 메시지 생성
     */
    private List<Message> createVisionMessages(List<String> imageUrls) {
        List<Message> messages = new ArrayList<>();

        // System 메시지
        messages.add(Message.builder()
                .role("system")
                .content(createSystemPrompt())
                .build());

        // User 메시지 (텍스트 + 이미지들)
        List<ContentPart> contentParts = new ArrayList<>();

        // 텍스트 파트
        String textPrompt = createTextPrompt(imageUrls.size());
        contentParts.add(ContentPart.builder()
                .type("text")
                .text(textPrompt)
                .build());

        // 이미지 파트들
        for (String imageUrl : imageUrls) {
            contentParts.add(ContentPart.builder()
                    .type("image_url")
                    .imageUrl(ImageUrl.builder().url(imageUrl).build())
                    .build());
        }

        messages.add(Message.builder()
                .role("user")
                .content(contentParts)
                .build());

        return messages;
    }

    private String createSystemPrompt() {
        return """
            당신은 학생이 스스로 문제를 해결할 수 있도록 사고의 물꼬를 터주는 '학습 길잡이'입니다.
            직접적인 최종 정답이나 복잡한 수치 계산을 수행하는 대신, 문제를 해결하기 위한 '논리적 접근법'과 '단계별 가이드'를 제공하는 데 집중하세요.

            [분석 및 작성 원칙]
            1. Solve No-More: 수치를 대입해 최종 답을 계산하지 마세요. 대신 "A 공식을 사용해 B를 구한다"와 같은 '방법론'을 제시하세요.
            2. Thinking Step (solution): 
               - 1단계: 문제에서 주어진 조건(Knowns)과 구해야 하는 것(Unknowns)을 명확히 정의하세요.
               - 2단계: 이 문제를 풀기 위해 머릿속에서 꺼내야 할 핵심 공식이나 정의를 언급하세요.
               - 3단계: 어떤 순서로 식을 세우고 접근해야 하는지 '전략'을 설명하세요.
            3. Error Analysis (commonMistakes): 계산 실수보다는 '개념의 오용'이나 '조건을 잘못 읽는 경우' 등 사고의 오류를 짚어주세요.
            4. Vocabulary: 중/고등 교육과정 표준 용어를 사용하여 학생이 교과서에서 본 내용을 떠올릴 수 있게 하세요.

            [응답 형식]
            반드시 아래의 JSON 구조를 지키며, JSON 외에 다른 설명은 포함하지 마세요.
            문자열 값에는 LaTeX/마크다운 수식 표기나 이스케이프 문자를 사용하지 마세요.
            수식은 일반 한국어 문장으로 풀어서 작성하세요.
            {
              "subject": "과목명",
              "problemType": "문제 유형",
              "keyPoints": ["필요한 공식1", "핵심 개념2"],
              "solution": "이 문제는 [개념]을 활용하는 문제입니다. 먼저 조건을 정리하면... 그 다음 ... 방향으로 식을 세워보세요.",
              "commonMistakes": "이 조건(단서)을 놓치면 전혀 다른 방향으로 풀릴 수 있으니 주의하세요.",
              "studyTips": "이 문제를 풀기 위해 교과서의 [단원명] 파트를 다시 읽어보는 것을 추천합니다."
            }

            모든 내용은 한국어로 작성하며, 학생이 스스로 답을 찾을 수 있게 격려하는 톤을 유지하세요.
            """;
    }

    private String createTextPrompt(int imageCount) {
        if (imageCount == 1) {
            return "이미지에 있는 문제를 분석하여 위에서 요청한 JSON 형식으로 분석 결과를 제공해주세요.";
        } else {
            return String.format(
                    "다음 %d장의 이미지는 하나의 문제를 여러 장으로 나눈 것입니다. " +
                    "모든 이미지를 종합하여 하나의 문제로 분석하고, " +
                    "위에서 요청한 JSON 형식으로 분석 결과를 제공해주세요.",
                    imageCount
            );
        }
    }

    private ProblemAnalysisResult parseResponse(String response) {
        try {
            // JSON 형식이 아닌 경우 처리
            String jsonContent = extractJsonFromResponse(response);

            if (!looksLikeJsonObject(jsonContent)) {
                if (isImageNotAnalyzableResponse(jsonContent)) {
                    throw new NonRetryableAnalysisException("분석이 불가능한 이미지입니다.");
                }
                if (isRefusalResponse(jsonContent)) {
                    throw new NonRetryableAnalysisException("AI가 요청을 거절하여 분석을 진행할 수 없습니다.");
                }
                throw new NonRetryableAnalysisException("AI가 JSON 형식이 아닌 응답을 반환했습니다.");
            }

            Map<String, Object> parsed = readJsonMap(jsonContent);

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

    private boolean looksLikeJsonObject(String content) {
        return content.startsWith("{") && content.endsWith("}");
    }

    private boolean isImageNotAnalyzableResponse(String content) {
        return content.contains("분석할 수 없")
                || content.contains("인식할 수 없")
                || content.contains("명확하지 않")
                || content.contains("이미지가")
                || content.contains("이미지에 있는 문제")
                || content.contains("죄송하지만")
                || content.contains("다른 이미지를 제공");
    }

    private boolean isRefusalResponse(String content) {
        String lower = content.toLowerCase();
        return lower.contains("i'm sorry")
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
