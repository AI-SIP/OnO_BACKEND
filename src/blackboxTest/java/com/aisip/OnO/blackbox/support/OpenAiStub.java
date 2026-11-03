package com.aisip.OnO.blackbox.support;

import java.util.List;
import java.util.Map;

/**
 * OpenAI chat completions 자리에 둘 응답. 앱은 응답의 choices[0].message.content 를 JSON 으로 읽는다.
 */
public final class OpenAiStub {

    /** 분석 결과로 돌려줄 값. 정답 노출 검사(#406)에 걸리지 않게 풀이 글에는 답을 쓰지 않는다. */
    public static final Map<String, Object> ANALYSIS = Map.ofEntries(
            Map.entry("analyzable", true),
            Map.entry("internalSolution", "근의 공식에 대입한다"),
            Map.entry("finalAnswer", "x = 7"),
            Map.entry("answerValue", "7"),
            Map.entry("explanationCheck", "no_explanation"),
            Map.entry("subject", "수학"),
            Map.entry("problemType", "이차방정식"),
            Map.entry("keyPoints", List.of("근의 공식", "판별식")),
            Map.entry("solution", "식을 정리한 뒤 근의 공식을 적용합니다."),
            Map.entry("commonMistakes", "부호를 바꾸지 않고 옮기는 실수"),
            Map.entry("studyTips", "판별식부터 확인하는 습관을 들이세요."));

    public static final Map<String, Object> LEARNING_REPORT = Map.of(
            "strengths", List.of("꾸준한 복습"),
            "gaps", List.of("오답 재풀이 부족"),
            "actions", List.of("틀린 문제를 이틀 뒤 다시 풀기"),
            "nextWeekGoal", "주 3회 복습",
            "confidence", 80.0);

    private OpenAiStub() {
    }

    public static Map<String, Object> completion(Object content) {
        return Map.of(
                "id", "chatcmpl-blackbox",
                "object", "chat.completion",
                "model", "gpt-4o",
                "choices", List.of(Map.of(
                        "index", 0,
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", Api.write(content)))));
    }

    /** WireMock 매핑 파일. 학습 리포트 요청은 프롬프트 문구로 가른다. */
    static String defaultAnalysisMapping() {
        return Api.write(Map.of(
                "priority", 10,
                "request", Map.of("method", "POST", "urlPath", "/v1/chat/completions"),
                "response", Map.of("status", 200,
                        "headers", Map.of("Content-Type", "application/json"),
                        "body", Api.write(completion(ANALYSIS)))));
    }

    static String defaultLearningReportMapping() {
        return Api.write(Map.of(
                "priority", 9,
                "request", Map.of("method", "POST", "urlPath", "/v1/chat/completions",
                        "bodyPatterns", List.of(Map.of("contains", "학습 요약 데이터"))),
                "response", Map.of("status", 200,
                        "headers", Map.of("Content-Type", "application/json"),
                        "body", Api.write(completion(LEARNING_REPORT)))));
    }
}
