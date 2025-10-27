package com.aisip.OnO.backend.util.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Message {
    private String role;
    private Object content;  // String 또는 List<ContentPart>
}
