package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReviewIntervalCalculator {

    private static final int MAX_INTERVAL_DAYS = 30;

    /**
     * 마지막으로 틀린 뒤에 정답을 남긴 날이 이만큼 되면 추천 복습에서 뺀다. 연속일 필요는 없다.
     * 틀리거나 부분 정답이면 처음부터 다시 센다. 그래야 추천에서 빠진 뒤에 틀린 문제가 다시
     * 추천에 나온다. 같은 날 여러 번 맞힌 것은 하루로 센다. 추천 목록과 복습 알림 쿼리도 같은
     * 값으로 거른다.
     */
    public static final int MASTERY_THRESHOLD = 3;

    public record ReviewSchedule(LocalDate nextReviewAt, int reviewInterval, int consecutiveCorrectCount) {
        public boolean isMastered() {
            return nextReviewAt == null;
        }
    }

    /** 일정을 다시 계산할 때 쓰는 풀이 기록 한 건. */
    public record SolveMark(LocalDate practicedDate, AnswerStatus answerStatus) {
    }

    /**
     * @param previousCorrectDayCount 이번 기록을 빼고, 마지막으로 틀린 뒤에 정답을 남긴 날 수
     */
    public static ReviewSchedule calculate(AnswerStatus status, int currentInterval, int currentConsecutiveCorrect,
                                           long previousCorrectDayCount) {
        return calculate(status, currentInterval, currentConsecutiveCorrect, previousCorrectDayCount,
                LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    /**
     * @param previousCorrectDayCount 이번 기록을 빼고, 마지막으로 틀린 뒤에 정답을 남긴 날 수
     * @param practicedDate           이번 기록을 남긴 날. 다음 복습일은 이 날부터 센다
     */
    public static ReviewSchedule calculate(AnswerStatus status, int currentInterval, int currentConsecutiveCorrect,
                                           long previousCorrectDayCount, LocalDate practicedDate) {
        return switch (status) {
            case CORRECT -> {
                int newConsecutive = currentConsecutiveCorrect + 1;
                if (previousCorrectDayCount + 1 >= MASTERY_THRESHOLD) {
                    yield new ReviewSchedule(null, currentInterval, newConsecutive);
                }
                int newInterval = Math.min(currentInterval * 2, MAX_INTERVAL_DAYS);
                yield new ReviewSchedule(practicedDate.plusDays(newInterval), newInterval, newConsecutive);
            }
            case WRONG, PARTIAL -> new ReviewSchedule(practicedDate.plusDays(1), 1, 0);
            default -> new ReviewSchedule(practicedDate.plusDays(3), currentInterval, currentConsecutiveCorrect);
        };
    }

    /**
     * 한 문제의 풀이 기록 전체로 지금 일정을 다시 계산한다.
     *
     * <p>기록을 남길 때마다 앞 상태에 이어 붙이면, 기록을 고치거나 지웠을 때 일정이 그대로 남는다.
     * 그래서 남기기, 고치기, 지우기 모두 처음부터 다시 계산한다.
     *
     * <p>이미 정답을 남긴 날에 다시 맞힌 기록은 건너뛴다. 같은 날 몰아 맞힌 것을 간격 복습으로
     * 보지 않기 위해서다. 틀리거나 부분 정답이면 정답을 남긴 날을 처음부터 다시 센다.
     *
     * @param firstReviewDate 기록이 하나도 없을 때의 복습일(등록한 날)
     * @param solves          시간순으로 정렬된 풀이 기록
     */
    public static ReviewSchedule replay(LocalDate firstReviewDate, List<SolveMark> solves) {
        ReviewSchedule schedule = new ReviewSchedule(firstReviewDate, 1, 0);
        Set<LocalDate> correctDays = new HashSet<>();

        for (SolveMark solve : solves) {
            if (solve.answerStatus() == AnswerStatus.CORRECT && correctDays.contains(solve.practicedDate())) {
                continue;
            }
            schedule = calculate(solve.answerStatus(), schedule.reviewInterval(),
                    schedule.consecutiveCorrectCount(), correctDays.size(), solve.practicedDate());
            if (solve.answerStatus() == AnswerStatus.CORRECT) {
                correctDays.add(solve.practicedDate());
            } else if (solve.answerStatus() == AnswerStatus.WRONG
                    || solve.answerStatus() == AnswerStatus.PARTIAL) {
                correctDays.clear();
            }
        }
        return schedule;
    }

    /**
     * 정답 날 수와 졸업한 날.
     *
     * @param correctDayCount 마지막으로 틀린 뒤에 정답을 남긴 서로 다른 날 수
     * @param masteredOn      지금 졸업한 상태일 때, 정답 날이 {@link #MASTERY_THRESHOLD} 일째가 된 날.
     *                        졸업하지 않았으면 {@code null}
     */
    public record MasteryProgress(int correctDayCount, LocalDate masteredOn) {
        public boolean isMastered() {
            return correctDayCount >= MASTERY_THRESHOLD;
        }
    }

    /**
     * 풀이 기록 전체로 지금 졸업했는지와 언제 졸업했는지를 센다.
     *
     * <p>{@link #replay} 와 같은 규칙이다. 같은 날 정답은 하루로 세고, 틀리거나 부분 정답이면 정답 날을
     * 비우고, UNKNOWN 은 건드리지 않는다. 학습 보고서가 "확실히 아는 문제"와 "이번 기간에 졸업한 문제"를
     * 세는 데 쓴다. 일정 계산과 졸업 판정이 따로 놀면 문제 상세의 정답 n/3 과 보고서 숫자가 어긋나서
     * 같은 클래스에 둔다.
     *
     * @param solves 시간순으로 정렬된 풀이 기록
     */
    public static MasteryProgress masteryProgress(List<SolveMark> solves) {
        Set<LocalDate> correctDays = new HashSet<>();
        LocalDate masteredOn = null;

        for (SolveMark solve : solves) {
            switch (solve.answerStatus()) {
                case CORRECT -> {
                    if (correctDays.add(solve.practicedDate()) && correctDays.size() == MASTERY_THRESHOLD) {
                        masteredOn = solve.practicedDate();
                    }
                }
                case WRONG, PARTIAL -> {
                    correctDays.clear();
                    masteredOn = null;
                }
                default -> {
                }
            }
        }
        return new MasteryProgress(correctDays.size(), masteredOn);
    }

    private ReviewIntervalCalculator() {}
}
