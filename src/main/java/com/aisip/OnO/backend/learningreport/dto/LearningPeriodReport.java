package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record LearningPeriodReport(
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        Long reviewCount,
        Long noteWriteCount,
        Long notePracticeCount,
        Double averageAccuracy,
        Integer consecutiveLearningDays,
        Double averageStudyTimeMinutes,
        List<LearningTrendPoint> trend,
        List<LearningWeakArea> weakAreas
) {
}
