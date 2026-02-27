package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningComparison;
import com.aisip.OnO.backend.learningreport.dto.LearningPeriodReport;
import com.aisip.OnO.backend.learningreport.dto.LearningRecommendations;
import com.aisip.OnO.backend.learningreport.dto.LearningTrendPoint;
import com.aisip.OnO.backend.learningreport.dto.LearningWeakArea;
import com.aisip.OnO.backend.learningreport.repository.LearningReportQueryRepository;
import com.aisip.OnO.backend.util.ai.OpenAIClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningReportService {

    private static final int TOP_WEAK_AREAS_LIMIT = 3;

    private final LearningReportQueryRepository reportRepository;
    private final OpenAIClient openAIClient;

    public LearningReportResponseDto getLearningReport(Long userId, LocalDate baseDate) {
        LocalDate targetDate = baseDate == null ? LocalDate.now() : baseDate;

        DateRange weekRange = weekRange(targetDate);
        DateRange previousWeekRange = previousWeekRange(weekRange);
        DateRange monthRange = monthRange(targetDate);
        DateRange previousMonthRange = previousMonthRange(monthRange);

        LearningPeriodReport weekly = buildPeriodReport(userId, "WEEKLY", weekRange, TrendType.DAILY);
        LearningPeriodReport previousWeekly = buildPeriodReport(userId, "PREVIOUS_WEEKLY", previousWeekRange, TrendType.DAILY);
        LearningPeriodReport monthly = buildPeriodReport(userId, "MONTHLY", monthRange, TrendType.WEEKLY);
        LearningPeriodReport previousMonthly = buildPeriodReport(userId, "PREVIOUS_MONTHLY", previousMonthRange, TrendType.WEEKLY);
        LearningPeriodReport total = buildTotalReport(userId, targetDate);
        LearningComparison weeklyComparison = buildComparison("WEEKLY", "PREVIOUS_WEEKLY", weekly, previousWeekly);
        LearningComparison monthlyComparison = buildComparison("MONTHLY", "PREVIOUS_MONTHLY", monthly, previousMonthly);
        LearningRecommendations recommendations = buildRecommendations(
                userId, weekly, monthly, total, weeklyComparison, monthlyComparison
        );

        return LearningReportResponseDto.builder()
                .weekly(weekly)
                .monthly(monthly)
                .total(total)
                .weeklyComparison(weeklyComparison)
                .monthlyComparison(monthlyComparison)
                .recommendations(recommendations)
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

        if (trendType == TrendType.WEEKLY) {
            int weekCount = weekCountInRange(startDate, endDate);
            for (int week = 1; week <= weekCount; week++) {
                buckets.put(weekLabel(week), 0L);
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
        if (trendType == TrendType.DAILY) {
            return date.toString();
        }
        if (trendType == TrendType.WEEKLY) {
            return weekLabel(weekOfMonth(date));
        }
        return yearMonthLabel(date);
    }

    private String yearMonthLabel(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    private int weekOfMonth(LocalDate date) {
        return ((date.getDayOfMonth() - 1) / 7) + 1;
    }

    private int weekCountInRange(LocalDate startDate, LocalDate endDate) {
        int dayCount = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return ((dayCount - 1) / 7) + 1;
    }

    private String weekLabel(int week) {
        return week + "주차";
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

    private LearningRecommendations buildRecommendations(
            Long userId,
            LearningPeriodReport weekly,
            LearningPeriodReport monthly,
            LearningPeriodReport total,
            LearningComparison weeklyComparison,
            LearningComparison monthlyComparison
    ) {
        LearningRecommendations fallback = buildRuleBasedRecommendations(weekly, monthly, total, weeklyComparison, monthlyComparison);

        Map<String, Object> summaryPayload = new LinkedHashMap<>();
        summaryPayload.put("userId", userId);
        summaryPayload.put("weekly", weekly);
        summaryPayload.put("monthly", monthly);
        summaryPayload.put("total", total);
        summaryPayload.put("weeklyComparison", weeklyComparison);
        summaryPayload.put("monthlyComparison", monthlyComparison);
        summaryPayload.put("ruleBasedRecommendations", fallback);

        return openAIClient.recommendLearningReport(summaryPayload)
                .map(ai -> mergeRecommendations(fallback, ai))
                .orElse(fallback);
    }

    private LearningRecommendations mergeRecommendations(LearningRecommendations fallback, LearningRecommendations ai) {
        return LearningRecommendations.builder()
                .strengths(safeList(ai.strengths(), fallback.strengths()))
                .gaps(safeList(ai.gaps(), fallback.gaps()))
                .actions(safeList(ai.actions(), fallback.actions()))
                .nextWeekGoal(safeString(ai.nextWeekGoal(), fallback.nextWeekGoal()))
                .confidence(ai.confidence() == null ? fallback.confidence() : ai.confidence())
                .build();
    }

    private LearningRecommendations buildRuleBasedRecommendations(
            LearningPeriodReport weekly,
            LearningPeriodReport monthly,
            LearningPeriodReport total,
            LearningComparison weeklyComparison,
            LearningComparison monthlyComparison
    ) {
        List<String> strengths = new java.util.ArrayList<>();
        List<String> gaps = new java.util.ArrayList<>();
        List<String> actions = new java.util.ArrayList<>();

        if (weekly.consecutiveLearningDays() >= 3) {
            strengths.add("최근 주차에 연속 학습 흐름이 안정적으로 유지되고 있습니다.");
        }
        if (weeklyComparison.averageAccuracyChangeRate() > 0) {
            strengths.add("이전 주 대비 정답률이 상승했습니다.");
        }
        if (monthlyComparison.reviewCountChangeRate() > 0) {
            strengths.add("이전 달 대비 복습량이 증가해 학습 루틴이 강화되고 있습니다.");
        }
        if (strengths.isEmpty()) {
            strengths.add("기록이 누적되고 있어 개인화 분석의 정확도가 점점 좋아지고 있습니다.");
        }

        if (weekly.averageAccuracy() < 50.0) {
            gaps.add("최근 주간 정답률이 낮아 취약 유형에 대한 집중 복습이 필요합니다.");
        }
        if (weekly.averageStudyTimeMinutes() < 5.0) {
            gaps.add("문제당 학습 시간이 짧아 오답 원인 점검이 충분하지 않을 수 있습니다.");
        }
        if (!weekly.weakAreas().isEmpty()) {
            gaps.add("오답이 반복된 유형이 있어 개념 복습 우선순위 조정이 필요합니다.");
        }
        if (gaps.isEmpty()) {
            gaps.add("큰 약점은 없지만 학습량 변동을 줄이면 성과를 더 안정화할 수 있습니다.");
        }

        String topWeakArea = weekly.weakAreas().isEmpty() ? "최근 오답 유형" : weekly.weakAreas().get(0).topic();
        actions.add(topWeakArea + " 유형 문제를 다음 주에 3문제 이상 재풀이하세요.");
        actions.add("오답 문제를 푼 뒤 5분 동안 틀린 이유와 개선점을 1문장씩 기록하세요.");
        actions.add("연속 학습일 목표를 최소 " + Math.max(3, weekly.consecutiveLearningDays()) + "일로 설정하세요.");

        int nextWeekReviewTarget = Math.max(weekly.reviewCount().intValue() + 2, 5);
        String nextWeekGoal = "다음 주에는 복습 " + nextWeekReviewTarget + "회, 평균 정답률 "
                + Math.max(60, weekly.averageAccuracy().intValue()) + "%를 목표로 하세요.";

        Double confidence = total.reviewCount() >= 20 ? 85.0 : 70.0;

        return LearningRecommendations.builder()
                .strengths(limitSize(strengths, 3))
                .gaps(limitSize(gaps, 3))
                .actions(limitSize(actions, 3))
                .nextWeekGoal(nextWeekGoal)
                .confidence(confidence)
                .build();
    }

    private List<String> safeList(List<String> target, List<String> fallback) {
        if (target == null || target.isEmpty()) {
            return fallback;
        }
        return limitSize(target, 3);
    }

    private String safeString(String target, String fallback) {
        if (target == null || target.isBlank()) {
            return fallback;
        }
        return target;
    }

    private List<String> limitSize(List<String> source, int limit) {
        if (source.size() <= limit) {
            return source;
        }
        return source.subList(0, limit);
    }

    private enum TrendType {
        DAILY,
        WEEKLY,
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
