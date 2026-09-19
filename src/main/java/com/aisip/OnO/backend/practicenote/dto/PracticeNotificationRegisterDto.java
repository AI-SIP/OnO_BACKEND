package com.aisip.OnO.backend.practicenote.dto;

import java.util.List;

public record PracticeNotificationRegisterDto(
        int intervalDays,
        int hour,
        int minute,
        String repeatType,
        List<Integer> weekDays
) {

    private static final String WEEKLY = "weekly";

    /**
     * 주간 반복인데 요일을 하나도 고르지 않은 상태인지 확인한다.
     *
     * <p>예전에는 이 상태가 크론 변환에서 매일 발송으로 되돌아갔다. 사용자는 특정 요일만
     * 고른 줄 알면서 매일 알림을 받았고, 요청이 잘못됐다는 신호도 없었다.
     */
    public boolean isWeeklyWithoutWeekDays() {
        return WEEKLY.equalsIgnoreCase(repeatType) && (weekDays == null || weekDays.isEmpty());
    }
}
