package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

@Builder
public record LearningTrendPoint(
        String label,
        Long reviewCount
) {
}
