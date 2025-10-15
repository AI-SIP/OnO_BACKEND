package com.aisip.OnO.backend.util.ai;

import com.aisip.OnO.backend.util.ai.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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

        } catch (Exception e) {
            log.error("Error analyzing images: {}", e.getMessage(), e);
            throw new RuntimeException("이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
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
                당신은 학생들의 오답노트를 분석하는 교육 전문가입니다.
                이미지에 있는 문제를 분석하여 다음 정보를 JSON 형식으로 제공해주세요.

                응답은 반드시 유효한 JSON 형식이어야 하며, 다음 구조를 따라야 합니다:
                {
                  "subject": "과목명 (예: 수학, 영어, 과학, 국어, 사회 등)",
                  "problemType": "문제 유형 (예: 계산 문제, 서술형, 객관식, 증명 등)",
                  "keyPoints": ["핵심 개념1", "핵심 개념2", "핵심 개념3"],
                  "solution": "단계별 풀이 과정을 자세히 설명",
                  "commonMistakes": "이런 유형의 문제에서 학생들이 자주 하는 실수",
                  "studyTips": "이 문제를 마스터하기 위한 구체적인 학습 팁"
                }

                모든 내용은 한국어로 작성하며, JSON 형식만 반환하세요.
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

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(jsonContent, Map.class);

            return ProblemAnalysisResult.builder()
                    .subject((String) parsed.get("subject"))
                    .problemType((String) parsed.get("problemType"))
                    .keyPoints((List<String>) parsed.get("keyPoints"))
                    .solution((String) parsed.get("solution"))
                    .commonMistakes((String) parsed.get("commonMistakes"))
                    .studyTips((String) parsed.get("studyTips"))
                    .build();

        } catch (Exception e) {
            log.error("Error parsing response: {}", response, e);
            throw new RuntimeException("응답 파싱 중 오류가 발생했습니다.", e);
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
}
