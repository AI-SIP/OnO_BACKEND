package com.aisip.OnO.backend.learningcalendar.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record LearningCalendarResponseDto(
        int year,
        int month,
        int currentStreak,
        int bestStreak,
        int thisMonthStudyDays,
        List<DailyStudyRecord> records
) {

    @Builder
    public record DailyStudyRecord(
            LocalDate date,
            boolean hasStudied,
            int reviewCount,
            int noteWriteCount,
            int studyMinutes,
            List<String> reviewedItems
    ) {
    }
}
