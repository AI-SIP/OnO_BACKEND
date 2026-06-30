package com.aisip.OnO.backend.studyroom.dto;

public record StudyRoomTodayPracticeSummary(int todayPracticeMemberCount, int todayPracticeCount) {

    public static StudyRoomTodayPracticeSummary empty() {
        return new StudyRoomTodayPracticeSummary(0, 0);
    }
}
