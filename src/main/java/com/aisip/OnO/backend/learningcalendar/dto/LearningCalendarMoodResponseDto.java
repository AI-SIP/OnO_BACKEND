package com.aisip.OnO.backend.learningcalendar.dto;

import java.time.LocalDate;

public record LearningCalendarMoodResponseDto(
        LocalDate date,
        String emojiKey
) {
}
