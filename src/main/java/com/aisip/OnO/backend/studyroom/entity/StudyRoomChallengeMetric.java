package com.aisip.OnO.backend.studyroom.entity;

public enum StudyRoomChallengeMetric {
    PROBLEM_COUNT,
    PRACTICE_COUNT,
    ATTENDANCE,

    // 기존에 저장된 챌린지 역직렬화 호환용 — 신규 생성 시에는 사용하지 않는다.
    @Deprecated
    WEEKLY_PROBLEM_COUNT,
    @Deprecated
    WEEKLY_PRACTICE_COUNT,
    @Deprecated
    STREAK
}
