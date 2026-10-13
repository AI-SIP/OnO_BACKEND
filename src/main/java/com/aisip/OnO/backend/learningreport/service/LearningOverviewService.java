package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.NoteStatus;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.Previous;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.Summary;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.TrendBucket;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.WeakFolder;
import com.aisip.OnO.backend.learningreport.repository.LearningOverviewQueryRepository;
import com.aisip.OnO.backend.learningreport.repository.LearningOverviewQueryRepository.DailyAnswerCount;
import com.aisip.OnO.backend.learningreport.repository.LearningOverviewQueryRepository.FolderAnswerCount;
import com.aisip.OnO.backend.learningreport.repository.LearningOverviewQueryRepository.SolveMarkRow;
import com.aisip.OnO.backend.problem.service.ReviewIntervalCalculator;
import com.aisip.OnO.backend.problem.service.ReviewIntervalCalculator.MasteryProgress;
import com.aisip.OnO.backend.problem.service.ReviewIntervalCalculator.SolveMark;
import com.aisip.OnO.backend.studyroom.service.StudyRoomStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 학습 보고서 개요({@code GET /api/learning-reports/overview}).
 *
 * <p>기존 리포트와 달리 캐시하지 않는다. 오늘 푼 기록까지 바로 보여 줘야 하는데 리포트 캐시는 풀이를
 * 저장해도 무효화되지 않는다. 집계가 전부 사용자 단위 조회라 쿼리 수가 데이터 양에 따라 늘지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningOverviewService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 기존 리포트와 같은 경계. {@code datetime(6)} 의 23:59:59.xxxxxx 기록이 빠지지 않게 마이크로초까지 잡는다. */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59, 999_999_000);

    private static final int WEAK_FOLDER_LIMIT = 3;
    private static final int TOTAL_TREND_MONTHS = 6;

    private final LearningOverviewQueryRepository overviewRepository;
    private final StudyRoomStatsService studyRoomStatsService;

    public LearningOverviewResponseDto getOverview(Long userId, LearningOverviewPeriod period, LocalDate baseDate) {
        return getOverview(userId, period, baseDate, LocalDate.now(KST));
    }

    LearningOverviewResponseDto getOverview(Long userId, LearningOverviewPeriod period, LocalDate baseDate,
                                            LocalDate today) {
        LocalDate base = baseDate == null || baseDate.isAfter(today) ? today : baseDate;
        // 공부한 날 수, 연속일, 처음 공부한 날을 학습 달력과 같은 날짜 집합 하나로 낸다.
        TreeSet<LocalDate> studyDates = studyRoomStatsService.studyDates(userId, today);

        DateRange range = periodRange(period, base, today, studyDates);
        LocalDate countedEnd = range.end().isAfter(today) ? today : range.end();
        DateRange previousRange = previousRange(period, range, today);
        List<DateRange> trendRanges = trendRanges(period, range, today);

        TreeMap<LocalDate, AnswerTally> daily = loadDaily(userId, range, previousRange, trendRanges, countedEnd);

        AnswerTally current = range.start() == null ? new AnswerTally() : sum(daily, range.start(), countedEnd);
        Summary summary = Summary.builder()
                .reviewCount(current.total)
                .accuracy(current.accuracy())
                .studyDays(range.start() == null ? 0 : countStudyDays(studyDates, range.start(), countedEnd))
                .currentStreak(studyRoomStatsService.currentStreak(studyDates, today))
                .build();

        Previous previous = null;
        if (previousRange != null) {
            AnswerTally before = sum(daily, previousRange.start(), previousRange.end());
            previous = Previous.builder()
                    .reviewCount(before.total)
                    .accuracy(before.accuracy())
                    .studyDays(countStudyDays(studyDates, previousRange.start(), previousRange.end()))
                    .build();
        }

        List<TrendBucket> trend = trendRanges.stream()
                .map(bucket -> TrendBucket.builder()
                        .startDate(bucket.start())
                        .endDate(bucket.end())
                        .reviewCount(sum(daily, bucket.start(), bucket.end()).total)
                        .build())
                .toList();

        boolean total = period == LearningOverviewPeriod.TOTAL;
        return LearningOverviewResponseDto.builder()
                .period(period)
                .startDate(range.start())
                .endDate(range.end())
                .hasPrevious(!total && studyDates.lower(range.start()) != null)
                .hasNext(!total && range.end().isBefore(today))
                .summary(summary)
                .previous(previous)
                .noteStatus(buildNoteStatus(userId, range.start(), countedEnd))
                .weakFolders(buildWeakFolders(userId, total ? null : range.start(), countedEnd))
                .trend(trend)
                .build();
    }

    /** 달력 기준 기간. 전체는 처음 공부한 날부터 오늘까지이고, 공부한 날이 없으면 시작일이 없다. */
    private DateRange periodRange(LearningOverviewPeriod period, LocalDate base, LocalDate today,
                                  TreeSet<LocalDate> studyDates) {
        return switch (period) {
            case WEEK -> {
                LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(monday, monday.plusDays(6));
            }
            case MONTH -> {
                YearMonth month = YearMonth.from(base);
                yield new DateRange(month.atDay(1), month.atEndOfMonth());
            }
            case TOTAL -> new DateRange(studyDates.isEmpty() ? null : studyDates.first(), today);
        };
    }

    /**
     * 비교 기간. 이미 끝난 기간이면 바로 앞 기간 전체와 비교한다. 진행 중이면 지난 기간도 같은 날 수만큼만
     * 센다. 주 초반에 일주일 전체와 비교하면 늘 줄어든 것처럼 보여서다. 이번 달이 지난달보다 길면 지난달 말일에서 자른다.
     */
    private DateRange previousRange(LearningOverviewPeriod period, DateRange range, LocalDate today) {
        DateRange full = switch (period) {
            case WEEK -> new DateRange(range.start().minusWeeks(1), range.start().minusDays(1));
            case MONTH -> new DateRange(range.start().minusMonths(1), range.start().minusDays(1));
            case TOTAL -> null;
        };
        if (full == null || range.end().isBefore(today)) {
            return full;
        }
        long elapsedDays = ChronoUnit.DAYS.between(range.start(), today);
        LocalDate sameLengthEnd = full.start().plusDays(elapsedDays);
        return new DateRange(full.start(), sameLengthEnd.isAfter(full.end()) ? full.end() : sameLengthEnd);
    }

    /** 주는 하루씩 7칸, 달은 월~일로 자른 주(달 경계에서 자른다), 전체는 최근 6개월을 달마다. 오늘 이후 칸도 넣는다. */
    private List<DateRange> trendRanges(LearningOverviewPeriod period, DateRange range, LocalDate today) {
        List<DateRange> buckets = new ArrayList<>();
        switch (period) {
            case WEEK -> {
                for (int i = 0; i < 7; i++) {
                    LocalDate day = range.start().plusDays(i);
                    buckets.add(new DateRange(day, day));
                }
            }
            case MONTH -> {
                LocalDate cursor = range.start();
                while (!cursor.isAfter(range.end())) {
                    LocalDate sunday = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                    LocalDate end = sunday.isAfter(range.end()) ? range.end() : sunday;
                    buckets.add(new DateRange(cursor, end));
                    cursor = end.plusDays(1);
                }
            }
            case TOTAL -> {
                YearMonth thisMonth = YearMonth.from(today);
                for (int i = TOTAL_TREND_MONTHS - 1; i >= 0; i--) {
                    YearMonth month = thisMonth.minusMonths(i);
                    buckets.add(new DateRange(month.atDay(1), month.atEndOfMonth()));
                }
            }
        }
        return buckets;
    }

    /** 요약, 비교 기간, 막대 그래프가 겹치는 날짜 범위를 한 번에 읽어 날짜별로 담는다. */
    private TreeMap<LocalDate, AnswerTally> loadDaily(Long userId, DateRange range, DateRange previousRange,
                                                      List<DateRange> trendRanges, LocalDate countedEnd) {
        LocalDate from = trendRanges.get(0).start();
        if (range.start() != null && range.start().isBefore(from)) {
            from = range.start();
        }
        if (previousRange != null && previousRange.start().isBefore(from)) {
            from = previousRange.start();
        }

        TreeMap<LocalDate, AnswerTally> daily = new TreeMap<>();
        for (DailyAnswerCount row : overviewRepository.findDailyAnswerCounts(
                userId, from.atStartOfDay(), countedEnd.atTime(END_OF_DAY))) {
            daily.computeIfAbsent(row.practicedDate(), key -> new AnswerTally()).add(row);
        }
        return daily;
    }

    private AnswerTally sum(TreeMap<LocalDate, AnswerTally> daily, LocalDate start, LocalDate end) {
        AnswerTally tally = new AnswerTally();
        if (start.isAfter(end)) {
            return tally;
        }
        daily.subMap(start, true, end, true).values().forEach(tally::add);
        return tally;
    }

    private int countStudyDays(TreeSet<LocalDate> studyDates, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            return 0;
        }
        return studyDates.subSet(start, true, end, true).size();
    }

    /**
     * 오답노트 세 단계. 지난 기간을 봐도 오늘 상태다. 졸업 기준은 추천 복습과 같은
     * {@link ReviewIntervalCalculator#masteryProgress} 를 쓴다. {@code nextReviewAt IS NULL} 로 판정하면
     * 백필되지 않은 옛 문제가 졸업한 것으로 잡힌다.
     *
     * <p>기간 중에 졸업했다가 다시 틀린 문제는 지금 졸업 상태가 아니라 새로 알게 된 수에 넣지 않는다.
     */
    private NoteStatus buildNoteStatus(Long userId, LocalDate start, LocalDate end) {
        long totalCount = overviewRepository.countProblems(userId);

        Map<Long, List<SolveMark>> marksByProblem = new LinkedHashMap<>();
        for (SolveMarkRow row : overviewRepository.findSolveMarks(userId)) {
            marksByProblem.computeIfAbsent(row.problemId(), key -> new ArrayList<>())
                    .add(new SolveMark(row.practicedAt().toLocalDate(), row.answerStatus()));
        }

        int knownCount = 0;
        int newlyKnownCount = 0;
        for (List<SolveMark> marks : marksByProblem.values()) {
            MasteryProgress progress = ReviewIntervalCalculator.masteryProgress(marks);
            if (!progress.isMastered()) {
                continue;
            }
            knownCount++;
            LocalDate masteredOn = progress.masteredOn();
            if (start != null && !masteredOn.isBefore(start) && !masteredOn.isAfter(end)) {
                newlyKnownCount++;
            }
        }

        int solvedCount = marksByProblem.size();
        return NoteStatus.builder()
                .totalCount(Math.toIntExact(totalCount))
                .knownCount(knownCount)
                .unsureCount(solvedCount - knownCount)
                // 두 쿼리 사이에 문제가 지워져도 음수가 나가지 않게 막는다.
                .unsolvedCount(Math.max(0, Math.toIntExact(totalCount) - solvedCount))
                .newlyKnownCount(newlyKnownCount)
                .knownThreshold(ReviewIntervalCalculator.MASTERY_THRESHOLD)
                .build();
    }

    /** 정답률 낮은 순, 같으면 WRONG 많은 순, 그다음 폴더 id 순으로 3개. WRONG 이 없는 폴더는 약한 곳이 아니라 뺀다. */
    private List<WeakFolder> buildWeakFolders(Long userId, LocalDate start, LocalDate end) {
        return overviewRepository.findFolderAnswerCounts(
                        userId, start == null ? null : start.atStartOfDay(), end.atTime(END_OF_DAY))
                .stream()
                .filter(row -> row.wrongCount() > 0)
                .map(this::toWeakFolder)
                .sorted(Comparator.comparing(WeakFolder::accuracy)
                        .thenComparing(WeakFolder::wrongCount, Comparator.reverseOrder())
                        .thenComparing(WeakFolder::folderId))
                .limit(WEAK_FOLDER_LIMIT)
                .toList();
    }

    private WeakFolder toWeakFolder(FolderAnswerCount row) {
        return WeakFolder.builder()
                .folderId(row.folderId())
                .name(row.name())
                .solveCount(row.totalCount())
                .wrongCount(row.wrongCount())
                .accuracy(accuracy(row.correctCount(), row.partialCount(), row.wrongCount()))
                .build();
    }

    /** UNKNOWN 은 분자와 분모 모두에서 뺀다. 채점할 기록이 없으면 0% 가 아니라 값이 없다. */
    private static Double accuracy(long correct, long partial, long wrong) {
        long graded = correct + partial + wrong;
        if (graded == 0) {
            return null;
        }
        double value = (correct + partial * 0.5) / graded * 100.0;
        return Math.round(value * 10.0) / 10.0;
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private static final class AnswerTally {
        private long total;
        private long correct;
        private long partial;
        private long wrong;

        void add(DailyAnswerCount row) {
            total += row.count();
            switch (row.answerStatus()) {
                case CORRECT -> correct += row.count();
                case PARTIAL -> partial += row.count();
                case WRONG -> wrong += row.count();
                default -> {
                }
            }
        }

        void add(AnswerTally other) {
            total += other.total;
            correct += other.correct;
            partial += other.partial;
            wrong += other.wrong;
        }

        Double accuracy() {
            return LearningOverviewService.accuracy(correct, partial, wrong);
        }
    }
}
