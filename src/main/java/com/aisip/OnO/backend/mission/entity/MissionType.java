package com.aisip.OnO.backend.mission.entity;

import lombok.Getter;

@Getter
public enum MissionType {
    USER_LOGIN(15L, AbilityType.ATTENDANCE),
    PROBLEM_WRITE(10L, AbilityType.NOTE_WRITE),
    PROBLEM_PRACTICE(5L, AbilityType.PROBLEM_PRACTICE),
    NOTE_PRACTICE(15L, AbilityType.NOTE_PRACTICE);

    private final Long point;
    private final AbilityType abilityType;

    MissionType(Long point, AbilityType abilityType) {
        this.point = point;
        this.abilityType = abilityType;
    }

    public enum AbilityType {
        ATTENDANCE,         // 데일리 출석
        NOTE_WRITE,        // 오답노트 작성
        PROBLEM_PRACTICE,  // 문제 복습
        NOTE_PRACTICE      // 복습노트 사용
    }
}
