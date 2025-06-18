package com.aisip.OnO.backend.practicenote.dto;

import java.util.List;

public record PracticeNotificationRegisterDto(
        int intervalDays,
        int hour,
        int minute,
        String repeatType,
        List<Integer> weekDays
) {
}
