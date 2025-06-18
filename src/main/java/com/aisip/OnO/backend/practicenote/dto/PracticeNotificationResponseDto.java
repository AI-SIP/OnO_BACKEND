package com.aisip.OnO.backend.practicenote.dto;

import com.aisip.OnO.backend.practicenote.entity.PracticeNotification;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record PracticeNotificationResponseDto(
        int intervalDays,
        int hour,
        int minute,
        String repeatType,
        List<Integer> weekDays
) {
    public static PracticeNotificationResponseDto from(@NotNull PracticeNotification practiceNotification) {
        return PracticeNotificationResponseDto.builder()
                .intervalDays(practiceNotification.getIntervalDays())
                .hour(practiceNotification.getHour())
                .minute(practiceNotification.getMinute())
                .repeatType(practiceNotification.getRepeatType())
                .weekDays(practiceNotification.getWeekDays())
                .build();
    }
}
