package com.aisip.OnO.backend.util.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;
}
