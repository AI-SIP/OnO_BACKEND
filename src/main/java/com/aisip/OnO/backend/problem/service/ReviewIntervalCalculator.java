package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;

import java.time.LocalDate;
import java.time.ZoneId;

public class ReviewIntervalCalculator {

    private static final int MAX_INTERVAL_DAYS = 30;

    /**
     * 이만큼 맞히면 추천 복습에서 뺀다. 연속일 필요는 없고, 중간에 틀려도 정답 기록이 이만큼 쌓이면 된다.
     * 추천 목록과 복습 알림 쿼리도 같은 값으로 거른다.
     */
    public static final int MASTERY_THRESHOLD = 3;

    public record ReviewSchedule(LocalDate nextReviewAt, int reviewInterval, int consecutiveCorrectCount) {
        public boolean isMastered() {
            return nextReviewAt == null;
        }
    }

    /**
     * @param previousCorrectCount 이번 기록을 빼고 지금까지 남긴 정답 기록 수
     */
    public static ReviewSchedule calculate(AnswerStatus status, int currentInterval, int currentConsecutiveCorrect,
                                           long previousCorrectCount) {
        return switch (status) {
            case CORRECT -> {
                int newConsecutive = currentConsecutiveCorrect + 1;
                if (previousCorrectCount + 1 >= MASTERY_THRESHOLD) {
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