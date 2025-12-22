package com.aisip.OnO.backend.util.ai.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatCompletionResponse {
    private List<Choice> choices;

    @Getter
    @NoArgsConstructor
    public static class Choice {
        private MessageResponse message;
    }

    @Getter
    @NoArgsConstructor
    public static class MessageResponse {
        private String content;
    }
}
