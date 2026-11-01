package com.aisip.OnO.backend.util.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * null 인 필드는 본문에 싣지 않는다. 학습 리포트 요청처럼 response_format 을 쓰지 않는 요청에
 * "response_format": null 이 실리지 않게 하기 위해서다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    /** Structured Outputs. 분석 요청에만 json_schema 를 넣는다. */
    @JsonProperty("response_format")
    private Object responseFormat;
}
