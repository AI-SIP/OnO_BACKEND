package com.aisip.OnO.backend.practicenote.dto;

import com.aisip.OnO.backend.practicenote.entity.PracticeNotification;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record PracticeNotificationResponseDto(
        int intervalDays,
        int hour,
        int minute,
        int notifyCount
) {
    public static PracticeNotificationResponseDto from(PracticeNotification practiceNotification) {
        return PracticeNotificationResponseDto.builder()
                .intervalDays(practiceNotification.getIntervalDays())
                .hour(practiceNotification.getHour())
                .minute(practiceNotification.getMinute())
                .notifyCount(practiceNotification.getNotifyCount())
                .build();
    }
}
