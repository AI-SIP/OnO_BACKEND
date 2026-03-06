package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

@Builder
public record LearningWeakArea(
        String topic,
        Long wrongCount
) {
}
