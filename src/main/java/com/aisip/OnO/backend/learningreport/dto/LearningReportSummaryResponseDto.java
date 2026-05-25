package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

@Builder
public record LearningReportSummaryResponseDto(
        String monthLabel,
        long monthlyReviewCount,
        int monthlyReviewGoal,
        long previousMonthlyReviewCount,
        long reviewCountDiff,
        double reviewCountChangeRate,
        String generatedAt,
        boolean monthlyReviewGoalAchieved,
        long remainingReviewCountToGoal,
        String summaryMessage
) {}