package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.repository.ReviewDueProblemProjection;
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
        public static ReviewDueProblemDto from(ReviewDueProblemProjection projection) {
            return ReviewDueProblemDto.builder()
                    .problemId(projection.problemId())
                    .memo(projection.memo())
                    .reference(projection.reference())
                    .nextReviewAt(projection.nextReviewAt())
                    .reviewInterval(projection.reviewInterval())
                    .consecutiveCorrectCount(projection.consecutiveCorrectCount())
                    .build();
        }
    }
}