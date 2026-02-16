package com.aisip.OnO.backend.problemsolve.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImprovementType {
    NO_REPEAT_MISTAKE("이전 실수를 반복하지 않았어요"),
    FOUND_NEW_SOLUTION("새로운 풀이법을 찾았어요"),
    BETTER_UNDERSTANDING("개념을 더 명확히 이해했어요"),
    FASTER_SOLVING("풀이 시간이 단축됐어요");

    private final String description;
}