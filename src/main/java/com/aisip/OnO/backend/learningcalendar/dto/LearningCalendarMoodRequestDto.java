package com.aisip.OnO.backend.learningcalendar.dto;

import java.time.LocalDate;

public record LearningCalendarMoodRequestDto(
        LocalDate date,
        String emojiKey
) {
}
