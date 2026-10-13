package com.aisip.OnO.backend.learningreport.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/learning-reports/overview} 응답.
 *
 * <p>날짜는 전부 {@code yyyy-MM-dd} 로 나간다. {@code startDate}, {@code endDate} 는 달력 기준 기간 전체라
 * 이번 주를 보면 {@code endDate} 가 미래일 수 있고, 집계는 오늘까지만 한다.
 */
@Builder
public record LearningOverviewResponseDto(
        LearningOverviewPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        boolean hasPrevious,
        boolean hasNext,
        Summary summary,
        Previous previous,
        NoteStatus noteStatus,
        List<WeakFolder> weakFolders,
        List<TrendBucket> trend
) {

    /**
     * @param accuracy      UNKNOWN 을 뺀 정답률(부분 정답은 0.5). 셀 기록이 없으면 {@code null}
     * @param currentStreak 기간과 상관없이 오늘 기준 연속 공부일. 학습 달력과 같은 값이다
     */
    @Builder
    public record Summary(
            long reviewCount,
            Double accuracy,
            int studyDays,
            int currentStreak
    ) {
    }

    /** 비교 기간 값. 기간이 아직 진행 중이면 지난 기간도 같은 날 수만큼만 센다. */
    @Builder
    public record Previous(
            long reviewCount,
            Double accuracy,
            int studyDays
    ) {
    }

    /**
     * 오답노트의 지금 상태. 지난 기간을 보고 있어도 오늘 기준이다.
     *
     * @param newlyKnownCount 지금 확실히 아는 문제 중 졸업한 날이 이 기간 안인 문제 수
     */
    @Builder
    public record NoteStatus(
            int totalCount,
            int knownCount,
            int unsureCount,
            int unsolvedCount,
            int newlyKnownCount,
            int knownThreshold
    ) {
    }

    /** @param wrongCount WRONG 수. 부분 정답은 넣지 않는다 */
    @Builder
    public record WeakFolder(
            Long folderId,
            String name,
            long solveCount,
            long wrongCount,
            Double accuracy
    ) {
    }

    @Builder
    public record TrendBucket(
            LocalDate startDate,
            LocalDate endDate,
            long reviewCount
    ) {
    }
}
