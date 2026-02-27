package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

@Builder
public record LearningComparison(
        String basePeriod,
        String compareTo,
        Double reviewCountChangeRate,
        Double averageAccuracyChangeRate,
        Double consecutiveLearningDaysChangeRate,
        Double averageStudyTimeChangeRate
) {
}
