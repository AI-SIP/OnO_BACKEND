package com.aisip.OnO.backend.mission.entity;

import lombok.Getter;

@Getter
public enum MissionType {
    USER_LOGIN(5L),
    PROBLEM_WRITE(5L),
    PROBLEM_PRACTICE(10L),
    NOTE_PRACTICE(15L);

    private final Long point;

    MissionType(Long point) {
        this.point = point;
    }
}
