package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;

import java.time.LocalDate;
import java.time.ZoneId;

public class ReviewIntervalCalculator {

    private static final int MAX_INTERVAL_DAYS = 30;
    static final int MASTERY_THRESHOLD = 3;

    public record ReviewSchedule(LocalDate nextReviewAt, int reviewInterval, int consecutiveCorrectCount) {
        public boolean isMastered() {
            return nextReviewAt == null;
        }
    }

    public static ReviewSchedule calculate(AnswerStatus status, int currentInterval, int currentConsecutiveCorrect) {
        return switch (status) {
            case CORRECT -> {
                int newConsecutive = currentConsecutiveCorrect + 1;
                if (newConsecutive >= MASTERY_THRESHOLD) {
                    yield new ReviewSchedule(null, currentInterval, newConsecutive);
                }
                int newInterval = Math.min(currentInterval * 2, MAX_INTERVAL_DAYS);
                yield new ReviewSchedule(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(newInterval), newInterval, newConsecutive);
            }
            case WRONG, PARTIAL -> new ReviewSchedule(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1), 1, 0);
            default -> new ReviewSchedule(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3), currentInterval, currentConsecutiveCorrect);
        };
    }

    private ReviewIntervalCalculator() {}
}