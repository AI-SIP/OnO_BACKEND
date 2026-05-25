package com.aisip.OnO.backend.learningcalendar.service;

import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.repository.LearningCalendarQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningCalendarService {

    private final LearningCalendarQueryRepository calendarRepository;

    public LearningCalendarResponseDto getLearningCalendar(Long userId, int year, int month) {
        return getLearningCalendar(userId, year, month, LocalDate.now());
    }

    LearningCalendarResponseDto getLearningCalendar(Long userId, int year, int month, LocalDate today) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        Map<LocalDate, LearningCalendarQueryRepository.DailyReviewStat> reviewStats = toReviewStatMap(
                calendarRepository.findDailyReviewStats(userId, start, end)
        );
        Map<LocalDate, LearningCalendarQueryRepository.DailyNoteWriteStat> noteWriteStats = toNoteWriteStatMap(
                calendarRepository.findDailyNoteWriteStats(userId, start, end)
        );
        Map<LocalDate, List<String>> reviewedItems = toReviewedItemsMap(
                calendarRepository.findReviewItems(userId, start, end)
        );

        List<LearningCalendarResponseDto.DailyStudyRecord> records = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> buildDailyRecord(
                        date,
                        reviewStats.get(date),
                        noteWriteStats.get(date),
                        reviewedItems.getOrDefault(date, List.of())
                ))
                .toList();

        TreeSet<LocalDate> studyDates = findStudyDates(userId);
        TreeSet<LocalDate> monthStudyDates = records.stream()
                .filter(LearningCalendarResponseDto.DailyStudyRecord::hasStudied)
                .map(LearningCalendarResponseDto.DailyStudyRecord::date)
                .collect(Collectors.toCollection(TreeSet::new));

        return LearningCalendarResponseDto.builder()
                .year(year)
                .month(month)
                .currentStreak(calculateCurrentStreak(studyDates, today))
                .bestStreak(calculateBestStreak(monthStudyDates))
                .thisMonthStudyDays((int) records.stream().filter(LearningCalendarResponseDto.DailyStudyRecord::hasStudied).count())
                .records(records)
                .build();
    }

    private LearningCalendarResponseDto.DailyStudyRecord buildDailyRecord(
            LocalDate date,
            LearningCalendarQueryRepository.DailyReviewStat reviewStat,
            LearningCalendarQueryRepository.DailyNoteWriteStat noteWriteStat,
            List<String> reviewedItems
    ) {
        int reviewCount = reviewStat == null ? 0 : toInt(reviewStat.reviewCount());
        int noteWriteCount = noteWriteStat == null ? 0 : toInt(noteWriteStat.noteWriteCount());
        int studyMinutes = reviewStat == null ? 0 : reviewStat.studyMinutes();

        return LearningCalendarResponseDto.DailyStudyRecord.builder()
                .date(date)
                .hasStudied(reviewCount + noteWriteCount > 0)
                .reviewCount(reviewCount)
                .noteWriteCount(noteWriteCount)
                .studyMinutes(studyMinutes)
                .reviewedItems(reviewedItems)
                .build();
    }

    private TreeSet<LocalDate> findStudyDates(Long userId) {
        TreeSet<LocalDate> studyDates = new TreeSet<>();
        studyDates.addAll(calendarRepository.findDistinctReviewDatesTotal(userId));
        studyDates.addAll(calendarRepository.findDistinctNoteWriteDatesTotal(userId));
        return studyDates;
    }

    private int calculateCurrentStreak(TreeSet<LocalDate> studyDates, LocalDate today) {
        if (studyDates.isEmpty()) {
            return 0;
        }

        LocalDate cursor = studyDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (studyDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int calculateBestStreak(TreeSet<LocalDate> studyDates) {
        if (studyDates.isEmpty()) {
            return 0;
        }

        int bestStreak = 1;
        int currentStreak = 1;
        LocalDate previous = null;
        for (LocalDate date : studyDates) {
            if (previous == null) {
                previous = date;
                continue;
            }
            if (date.equals(previous.plusDays(1))) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }
            bestStreak = Math.max(bestStreak, currentStreak);
            previous = date;
        }
        return bestStreak;
    }

    private Map<LocalDate, LearningCalendarQueryRepository.DailyReviewStat> toReviewStatMap(
            List<LearningCalendarQueryRepository.DailyReviewStat> stats
    ) {
        Map<LocalDate, LearningCalendarQueryRepository.DailyReviewStat> map = new HashMap<>();
        for (LearningCalendarQueryRepository.DailyReviewStat stat : stats) {
            map.put(stat.date(), stat);
        }
        return map;
    }

    private Map<LocalDate, LearningCalendarQueryRepository.DailyNoteWriteStat> toNoteWriteStatMap(
            List<LearningCalendarQueryRepository.DailyNoteWriteStat> stats
    ) {
        Map<LocalDate, LearningCalendarQueryRepository.DailyNoteWriteStat> map = new HashMap<>();
        for (LearningCalendarQueryRepository.DailyNoteWriteStat stat : stats) {
            map.put(stat.date(), stat);
        }
        return map;
    }

    private Map<LocalDate, List<String>> toReviewedItemsMap(
            List<LearningCalendarQueryRepository.ReviewItem> items
    ) {
        Map<LocalDate, LinkedHashMap<String, String>> grouped = new HashMap<>();
        for (LearningCalendarQueryRepository.ReviewItem item : items) {
            LinkedHashMap<String, String> titles = grouped.computeIfAbsent(item.date(), key -> new LinkedHashMap<>());
            if (titles.size() < 10) {
                titles.putIfAbsent(item.title(), item.title());
            }
        }

        Map<LocalDate, List<String>> result = new HashMap<>();
        for (Map.Entry<LocalDate, LinkedHashMap<String, String>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }
        return result;
    }

    private int toInt(long value) {
        return Math.toIntExact(value);
    }
}
