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
     * <p>이 상태는 크론 변환에서 매일 발송으로 되돌아간다. 사용자는 특정 요일만 고른 줄 알면서
     * 매일 알림을 받는다. 그래서 신버전 앱 요청은 진입부에서 400 으로 막는다. 요일을 고르라는
     * 검증이 없는 구버전 앱 요청은 예전 서버와 같게 매일로 저장한다.
     */
    public boolean isWeeklyWithoutWeekDays() {
        return WEEKLY.equalsIgnoreCase(repeatType) && (weekDays == null || weekDays.isEmpty());
    }
}
