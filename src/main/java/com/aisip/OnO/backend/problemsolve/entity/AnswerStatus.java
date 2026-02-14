package com.aisip.OnO.backend.problemsolve.entity;

public enum AnswerStatus {
    CORRECT,      // 정답
    WRONG,        // 오답
    PARTIAL,      // 부분 정답
    UNKNOWN       // 알 수 없음 (레거시 마이그레이션용)
}