package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

@Builder
public record LearningReportResponseDto(
        LearningPeriodReport weekly,
        LearningPeriodReport monthly,
        LearningPeriodReport total,
        LearningComparison weeklyComparison,
        LearningComparison monthlyComparison,
        LearningRecommendations recommendations
) {}
