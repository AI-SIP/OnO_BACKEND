package com.aisip.OnO.backend.achievement.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveMark;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 복습 기록을 시각 순서로 한 번 훑어 훈장 여섯 개가 쓸 값을 함께 만든다.
 *
 * <p>집념·불사조·새벽반·올빼미·무결점·회고왕이 전부 같은 표를 본다. 조건마다 쿼리를 따로 두면
 * 훈장 화면 한 번에 같은 표를 여섯 번 읽게 되고, 그중 둘(무결점·불사조)은 애초에 집계 함수로 안 된다.
 * 순서대로 한 번 훑는 것으로 여섯 개가 전부 나온다.
 */
public record SolveScan(
        long maxSolveCountOnOneProblem,
        long comebackCount,
        long dawnSolveCount,
        long nightSolveCount,
        long reflectionCount,
        long longestCorrectStreak
) {

    /** 새벽반이 보는 구간. 05:00 이상 08:00 미만. */
    private static final int DAWN_START_HOUR = 5;
    private static final int DAWN_END_HOUR = 8;

    /** 올빼미가 보는 구간. 00:00 이상 03:00 미만. 새벽반과 겹치지 않는다. */
    private static final int NIGHT_END_HOUR = 3;

    public static final SolveScan EMPTY = new SolveScan(0, 0, 0, 0, 0, 0);

    /**
     * @param marks 복습 시각 오름차순으로 정렬된 기록. 정렬이 깨지면 무결점과 불사조가 틀린다.
     */
    public static SolveScan of(List<ProblemSolveMark> marks) {
        if (marks.isEmpty()) {
            return EMPTY;
        }

        Map<Long, Long> solveCountByProblem = new HashMap<>();
        // 이 문제에서 틀린 적이 있는가. 불사조는 "틀린 뒤에 맞혔는가" 라 앞에 오답이 있었는지를 기억해야 한다.
        Set<Long> everWrongProblems = new HashSet<>();
        boolean comeback = false;

        long dawnCount = 0;
        long nightCount = 0;
        long reflectionCount = 0;
        long correctStreak = 0;
        long longestCorrectStreak = 0;

        for (ProblemSolveMark mark : marks) {
            solveCountByProblem.merge(mark.problemId(), 1L, Long::sum);

            if (isDawn(mark.practicedAt())) {
                dawnCount++;
            }
            if (isNight(mark.practicedAt())) {
                nightCount++;
            }
            if (mark.hasReflection()) {
                reflectionCount++;
            }

            AnswerStatus status = mark.answerStatus();
            if (status == AnswerStatus.WRONG) {
                everWrongProblems.add(mark.problemId());
            }
            // PARTIAL 은 정답으로 치지 않는다. 부분 정답은 아직 못 맞힌 것이다.
            if (status == AnswerStatus.CORRECT && everWrongProblems.contains(mark.problemId())) {
                comeback = true;
            }

            // UNKNOWN 은 레거시 마이그레이션이 남긴 값이라 세지도 않고 끊지도 않는다.
            // 그때 맞혔는지 아닌지를 모르는 것이라, 틀렸다고 보고 끊으면 없는 실패를 만드는 셈이다.
            if (status == AnswerStatus.CORRECT) {
                correctStreak++;
                longestCorrectStreak = Math.max(longestCorrectStreak, correctStreak);
            } else if (status == AnswerStatus.WRONG || status == AnswerStatus.PARTIAL) {
                correctStreak = 0;
            }
        }

        long maxSolveCount = solveCountByProblem.values().stream().mapToLong(Long::longValue).max().orElse(0);

        return new SolveScan(
                maxSolveCount,
                comeback ? 1 : 0,
                dawnCount,
                nightCount,
                reflectionCount,
                longestCorrectStreak
        );
    }

    /**
     * 복습 시각이 새벽 구간인지.
     *
     * <p>{@code practicedAt} 은 앱이 보낸 벽시계 시각을 그대로 담고 있고, 운영과 테스트 모두
     * JVM 시간대가 {@code Asia/Seoul} 로 못박혀 있다. 즉 이 값의 시(hour)가 곧 KST 시각이다.
     * 여기서 {@code atZone(UTC)} 같은 것을 한 번 더 태우면 아홉 시간이 밀려 엉뚱한 사람이 받는다.
     */
    private static boolean isDawn(LocalDateTime practicedAt) {
        int hour = practicedAt.getHour();
        return hour >= DAWN_START_HOUR && hour < DAWN_END_HOUR;
    }

    private static boolean isNight(LocalDateTime practicedAt) {
        return practicedAt.getHour() < NIGHT_END_HOUR;
    }
}
