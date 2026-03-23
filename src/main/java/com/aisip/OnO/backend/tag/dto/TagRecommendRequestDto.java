package com.aisip.OnO.backend.tag.dto;

import java.util.List;

public record TagRecommendRequestDto(
        List<String> imageUrls
) {
}
