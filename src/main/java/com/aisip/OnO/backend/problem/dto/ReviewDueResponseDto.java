package com.aisip.OnO.backend.problem.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record ReviewDueResponseDto(
        long dueCount,
        long overdueCount,
        List<ReviewDueProblemDto> problems
) {
    @Builder
    public record ReviewDueProblemDto(
            Long problemId,
            String memo,
            String reference,
            LocalDate nextReviewAt,
            int reviewInterval,
            int consecutiveCorrectCount
    ) {
    }
}