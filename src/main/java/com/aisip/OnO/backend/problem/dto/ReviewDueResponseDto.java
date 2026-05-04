package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.Problem;
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
        public static ReviewDueProblemDto from(Problem problem) {
            return ReviewDueProblemDto.builder()
                    .problemId(problem.getId())
                    .memo(problem.getMemo())
                    .reference(problem.getReference())
                    .nextReviewAt(problem.getNextReviewAt())
                    .reviewInterval(problem.getReviewInterval())
                    .consecutiveCorrectCount(problem.getConsecutiveCorrectCount())
                    .build();
        }
    }
}