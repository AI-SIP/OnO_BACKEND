package com.aisip.OnO.backend.util.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentPart {
    private String type;  // "text" 또는 "image_url"
    private String text;  // type이 "text"일 때

    @JsonProperty("image_url")
    private ImageUrl imageUrl;  // type이 "image_url"일 때
}
