package com.aisip.OnO.backend.util.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI 호출 전용 HTTP 클라이언트.
 *
 * OpenAIConfig 의 restTemplate 빈은 타임아웃이 없어 OpenAI 가 응답을 늦게 주면 분석 컨슈머 스레드가 무한히 묶인다.
 * 그 빈은 이름이 범용이라 다른 곳에서 주입받을 수 있으니 그대로 두고, AI 전용 빈을 따로 둔다.
 * defaultCandidate = false 라서 타입만으로 RestTemplate 을 주입받는 곳에는 끼어들지 않고,
 * @Qualifier(AI_REST_TEMPLATE) 로 이름을 댄 곳에만 주입된다.
 */
@Configuration
public class OpenAIHttpClientConfig {

    public static final String AI_REST_TEMPLATE = "aiRestTemplate";

    /** 연결 타임아웃 기본값(ms) */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;

    /**
     * 응답 읽기 타임아웃 기본값(ms). 2단계(#397)부터 모델이 내부 풀이까지 함께 써서 출력이 길어졌으므로 60초에서 90초로 늘렸다.
     * 이 값을 바꾸면 ProblemAnalysisDispatchGuard.TTL 도 함께 따져야 한다(큐 TTL + 시도 횟수 x 이 값보다 길어야 한다).
     */
    public static final int DEFAULT_READ_TIMEOUT_MS = 90_000;

    @Bean(name = AI_REST_TEMPLATE, defaultCandidate = false)
    public RestTemplate aiRestTemplate(
            @Value("${openai.http.connect-timeout-ms:" + DEFAULT_CONNECT_TIMEOUT_MS + "}") int connectTimeoutMs,
            @Value("${openai.http.read-timeout-ms:" + DEFAULT_READ_TIMEOUT_MS + "}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        // 이미지 여러 장 분석은 수십 초가 걸린다. 응답이 이보다 늦으면 재시도 대상 실패로 본다
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
