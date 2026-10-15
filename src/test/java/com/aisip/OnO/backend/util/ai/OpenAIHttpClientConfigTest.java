package com.aisip.OnO.backend.util.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AI 전용 RestTemplate 빈 배선. 전체 스프링 컨텍스트 없이 관련 설정만 올려 확인한다.
 *
 * 확인하는 것: OpenAIClient 는 타임아웃 있는 aiRestTemplate 을 받고,
 * 타입만으로 RestTemplate 을 주입받는 곳은 예전처럼 기존 restTemplate 을 받는다(모호성 없음).
 */
@DisplayName("AI 전용 HTTP 클라이언트 배선")
class OpenAIHttpClientConfigTest {

    static class PlainRestTemplateUser {
        @Autowired
        RestTemplate restTemplate;
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(OpenAIConfig.class, OpenAIHttpClientConfig.class, OpenAIClient.class, PlainRestTemplateUser.class);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(MeterRegistry.class, () -> new SimpleMeterRegistry());
        context.registerBean(AnalysisImageLoader.class, () -> mock(AnalysisImageLoader.class));
        context.refresh();
        return context;
    }

    private static final Map<String, Object> OPENAI_PROPERTIES = Map.of(
            "openai.model", "gpt-4o",
            "openai.api.key", "k",
            "openai.api.url", "https://api.openai.com/v1/chat/completions"
    );

    @Test
    @DisplayName("OpenAIClient 는 aiRestTemplate 을, 타입 주입은 기존 restTemplate 을 받는다")
    void injectsQualifiedTemplateOnlyIntoOpenAIClient() {
        try (AnnotationConfigApplicationContext context = context(OPENAI_PROPERTIES)) {
            RestTemplate aiRestTemplate = context.getBean(OpenAIHttpClientConfig.AI_REST_TEMPLATE, RestTemplate.class);
            RestTemplate generalRestTemplate = context.getBean("restTemplate", RestTemplate.class);

            assertThat(aiRestTemplate).isNotSameAs(generalRestTemplate);
            assertThat(ReflectionTestUtils.getField(context.getBean(OpenAIClient.class), "restTemplate"))
                    .isSameAs(aiRestTemplate);
            assertThat(context.getBean(PlainRestTemplateUser.class).restTemplate)
                    .as("타입으로만 주입받는 곳에 AI 전용 빈이 끼어들면 안 된다")
                    .isSameAs(generalRestTemplate);
            assertThat(context.getBean(RestTemplate.class)).isSameAs(generalRestTemplate);
        }
    }

    @Test
    @DisplayName("기본 타임아웃은 connect 5초, read 60초")
    void appliesDefaultTimeouts() {
        try (AnnotationConfigApplicationContext context = context(OPENAI_PROPERTIES)) {
            RestTemplate aiRestTemplate = context.getBean(OpenAIHttpClientConfig.AI_REST_TEMPLATE, RestTemplate.class);

            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) aiRestTemplate.getRequestFactory();
            assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(5000);
            assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(60000);
        }
    }

    @Test
    @DisplayName("타임아웃은 설정으로 바꿀 수 있다")
    void appliesConfiguredTimeouts() {
        Map<String, Object> properties = new java.util.HashMap<>(OPENAI_PROPERTIES);
        properties.put("openai.http.connect-timeout-ms", "1234");
        properties.put("openai.http.read-timeout-ms", "45678");
        try (AnnotationConfigApplicationContext context = context(properties)) {
            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory)
                    context.getBean(OpenAIHttpClientConfig.AI_REST_TEMPLATE, RestTemplate.class).getRequestFactory();

            assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(1234);
            assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(45678);
        }
    }
}
