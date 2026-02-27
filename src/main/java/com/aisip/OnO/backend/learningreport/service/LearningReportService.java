package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningComparison;
import com.aisip.OnO.backend.learningreport.dto.LearningPeriodReport;
import com.aisip.OnO.backend.learningreport.dto.LearningTrendPoint;
import com.aisip.OnO.backend.learningreport.dto.LearningWeakArea;
import com.aisip.OnO.backend.learningreport.repository.LearningReportQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningReportService {

    private static final int TOP_WEAK_AREAS_LIMIT = 3;

    private final LearningReportQueryRepository reportRepository;

    public LearningReportResponseDto getLearningReport(Long userId, LocalDate baseDate) {
        LocalDate targetDate = baseDate == null ? LocalDate.now() : baseDate;

        DateRange weekRange = weekRange(targetDate);
        DateRange previousWeekRange = previousWeekRange(weekRange);
        DateRange monthRange = monthRange(targetDate);
        DateRange previousMonthRange = previousMonthRange(monthRange);

        LearningPeriodReport weekly = buildPeriodReport(userId, "WEEKLY", weekRange, TrendType.DAILY);
        LearningPeriodReport previousWeekly = buildPeriodReport(userId, "PREVIOUS_WEEKLY", previousWeekRange, TrendType.DAILY);
        LearningPeriodReport monthly = buildPeriodReport(userId, "MONTHLY", monthRange, TrendType.DAILY);
        LearningPeriodReport previousMonthly = buildPeriodReport(userId, "PREVIOUS_MONTHLY", previousMonthRange, TrendType.DAILY);
        LearningPeriodReport total = buildTotalReport(userId, targetDate);

        return LearningReportResponseDto.builder()
                .weekly(weekly)
                .monthly(monthly)
                .total(total)
                .weeklyComparison(buildComparison("WEEKLY", "PREVIOUS_WEEKLY", weekly, previousWeekly))
                .monthlyComparison(buildComparison("MONTHLY", "PREVIOUS_MONTHLY", monthly, previousMonthly))
                .build();
    }

    private LearningPeriodReport buildPeriodReport(
            Long userId, String label, DateRange range, TrendType trendType
    ) {
        LocalDateTime start = range.start().atStartOfDay();
        LocalDateTime end = range.end().atTime(23, 59, 59);

        Long reviewCount = defaultLong(reportRepository.countReviewsInPeriod(userId, start, end));
        Double avgAccuracy = toPercent(reportRepository.averageAccuracyInPeriod(userId, start, end));
        Double avgStudyTime = secondsToMinutes(reportRepository.averageStudyTimeInPeriod(userId, start, end));
        List<LocalDate> practiceDates = reportRepository.findDistinctPracticeDatesInPeriod(userId, start, end);

        return LearningPeriodReport.builder()
                .periodLabel(label)
                .startDate(range.start())
                .endDate(range.end())
                .reviewCount(reviewCount)
                .averageAccuracy(avgAccuracy)
                .consecutiveLearningDays(calculateLongestStreak(practiceDates))
                .averageStudyTimeMinutes(avgStudyTime)
                .trend(buildTrend(userId, range.start(), range.end(), trendType))
                .weakAreas(buildWeakAreasInPeriod(userId, start, end))
                .build();
    }

    private LearningPeriodReport buildTotalReport(Long userId, LocalDate baseDate) {
        Long reviewCount = defaultLong(reportRepository.countReviewsTotal(userId));
        Double avgAccuracy = toPercent(reportRepository.averageAccuracyTotal(userId));
        Double avgStudyTime = secondsToMinutes(reportRepository.averageStudyTimeTotal(userId));
        List<LocalDate> practiceDates = reportRepository.findDistinctPracticeDatesTotal(userId);

        LocalDate trendStart = baseDate.minusMonths(5).withDayOfMonth(1);
        LocalDate trendEnd = YearMonth.from(baseDate).atEndOfMonth();

        return LearningPeriodReport.builder()
                .periodLabel("TOTAL")
                .startDate(null)
                .endDate(baseDate)
                .reviewCount(reviewCount)
                .averageAccuracy(avgAccuracy)
                .consecutiveLearningDays(calculateLongestStreak(practiceDates))
                .averageStudyTimeMinutes(avgStudyTime)
                .trend(buildTrend(userId, trendStart, trendEnd, TrendType.MONTHLY))
                .weakAreas(buildWeakAreasTotal(userId))
                .build();
    }

    private LearningComparison buildComparison(
            String basePeriod,
            String compareTo,
            LearningPeriodReport current,
            LearningPeriodReport previous
    ) {
        return LearningComparison.builder()
                .basePeriod(basePeriod)
                .compareTo(compareTo)
                .reviewCountChangeRate(changeRate(current.reviewCount().doubleValue(), previous.reviewCount().doubleValue()))
                .averageAccuracyChangeRate(changeRate(current.averageAccuracy(), previous.averageAccuracy()))
                .consecutiveLearningDaysChangeRate(changeRate(
                        current.consecutiveLearningDays().doubleValue(),
                        previous.consecutiveLearningDays().doubleValue()
                ))
                .averageStudyTimeChangeRate(changeRate(current.averageStudyTimeMinutes(), previous.averageStudyTimeMinutes()))
                .build();
    }

    private List<LearningTrendPoint> buildTrend(
            Long userId, LocalDate startDate, LocalDate endDate, TrendType trendType
    ) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        TreeMap<String, Long> bucket = initializeTrendBuckets(startDate, endDate, trendType);

        List<LearningReportQueryRepository.DailySolveCount> rows =
                reportRepository.findDailySolveCounts(userId, start, end);
        for (LearningReportQueryRepository.DailySolveCount row : rows) {
            String key = trendKey(row.practicedDate(), trendType);
            bucket.computeIfPresent(key, (k, v) -> v + defaultLong(row.solveCount()));
        }

        return bucket.entrySet().stream()
                .map(e -> LearningTrendPoint.builder()
                        .label(e.getKey())
                        .reviewCount(e.getValue())
                        .build())
                .toList();
    }

    private TreeMap<String, Long> initializeTrendBuckets(LocalDate startDate, LocalDate endDate, TrendType trendType) {
        TreeMap<String, Long> buckets = new TreeMap<>();
        if (trendType == TrendType.DAILY) {
            LocalDate cursor = startDate;
            while (!cursor.isAfter(endDate)) {
                buckets.put(cursor.toString(), 0L);
                cursor = cursor.plusDays(1);
            }
            return buckets;
        }

        LocalDate cursor = startDate.withDayOfMonth(1);
        while (!cursor.isAfter(endDate)) {
            buckets.put(yearMonthLabel(cursor), 0L);
            cursor = cursor.plusMonths(1);
        }
        return buckets;
    }

    private String trendKey(LocalDate date, TrendType trendType) {
        return trendType == TrendType.DAILY ? date.toString() : yearMonthLabel(date);
    }

    private String yearMonthLabel(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    private List<LearningWeakArea> buildWeakAreasInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        return reportRepository.findTopWeakAreasInPeriod(userId, start, end, TOP_WEAK_AREAS_LIMIT)
                .stream()
                .map(w -> LearningWeakArea.builder()
                        .topic(w.topic())
                        .wrongCount(defaultLong(w.wrongCount()))
                        .build())
                .toList();
    }

    private List<LearningWeakArea> buildWeakAreasTotal(Long userId) {
        return reportRepository.findTopWeakAreasTotal(userId, TOP_WEAK_AREAS_LIMIT)
                .stream()
                .map(w -> LearningWeakArea.builder()
                        .topic(w.topic())
                        .wrongCount(defaultLong(w.wrongCount()))
                        .build())
                .toList();
    }

    private int calculateLongestStreak(List<LocalDate> sortedDates) {
        if (sortedDates.isEmpty()) {
            return 0;
        }
        int maxStreak = 1;
        int currentStreak = 1;
        for (int i = 1; i < sortedDates.size(); i++) {
            LocalDate prev = sortedDates.get(i - 1);
            LocalDate cur = sortedDates.get(i);
            if (cur.equals(prev.plusDays(1))) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }
            maxStreak = Math.max(maxStreak, currentStreak);
        }
        return maxStreak;
    }

    private Double changeRate(Double current, Double previous) {
        double cur = current == null ? 0.0 : current;
        double prev = previous == null ? 0.0 : previous;
        if (prev == 0.0) {
            return cur == 0.0 ? 0.0 : 100.0;
        }
        return ((cur - prev) / prev) * 100.0;
    }

    private Double toPercent(Double rawAccuracy) {
        return rawAccuracy == null ? 0.0 : rawAccuracy * 100.0;
    }

    private Double secondsToMinutes(Double seconds) {
        return seconds == null ? 0.0 : seconds / 60.0;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private enum TrendType {
        DAILY,
        MONTHLY
    }

    private DateRange weekRange(LocalDate targetDate) {
        LocalDate start = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DateRange(start, start.plusDays(6));
    }

    private DateRange previousWeekRange(DateRange weekRange) {
        return new DateRange(weekRange.start().minusWeeks(1), weekRange.start().minusDays(1));
    }

    private DateRange monthRange(LocalDate targetDate) {
        LocalDate start = targetDate.withDayOfMonth(1);
        LocalDate end = YearMonth.from(targetDate).atEndOfMonth();
        return new DateRange(start, end);
    }

    private DateRange previousMonthRange(DateRange monthRange) {
        return new DateRange(monthRange.start().minusMonths(1), monthRange.start().minusDays(1));
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
