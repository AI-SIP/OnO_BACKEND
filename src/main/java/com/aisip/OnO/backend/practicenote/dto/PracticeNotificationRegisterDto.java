package com.aisip.OnO.backend.practicenote.dto;

public record PracticeNotificationRegisterDto(
        int intervalDays,
        int hour,
        int minute,
        int notifyCount
) {
}
