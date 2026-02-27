package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LearningRecommendations(
        List<String> strengths,
        List<String> gaps,
        List<String> actions,
        String nextWeekGoal,
        Double confidence
) {
}
