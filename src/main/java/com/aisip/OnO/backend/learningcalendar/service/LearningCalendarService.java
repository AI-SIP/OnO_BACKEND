package com.aisip.OnO.backend.learningcalendar.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodResponseDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.entity.LearningCalendarMood;
import com.aisip.OnO.backend.learningcalendar.exception.LearningCalendarErrorCase;
import com.aisip.OnO.backend.learningcalendar.repository.LearningCalendarMoodRepository;
import com.aisip.OnO.backend.learningcalendar.repository.LearningCalendarQueryRepository;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.service.MissionProgressUpdater;
import com.aisip.OnO.backend.util.redis.StreakCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningCalendarService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 하루의 마지막 순간. 집계 쿼리가 {@code BETWEEN start AND end} 로 도는데
     * {@code 23:59:59} 로 끊으면 {@code datetime(6)} 컬럼에 저장된
     * 23:59:59.000001 ~ 23:59:59.999999 구간의 기록이 통째로 빠진다.
     * 마이크로초 단위까지 포함하도록 경계를 잡는다.
     */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59, 999_999_000);

    private final LearningCalendarQueryRepository calendarRepository;
    private final StreakCacheService streakCacheService;
    private final LearningCalendarMoodRepository moodRepository;
    private final CustomEmojiValidator customEmojiValidator;
    private final MissionProgressUpdater missionProgressUpdater;

    public LearningCalendarResponseDto getLearningCalendar(Long userId, int year, int month) {
        // 서비스 대상이 국내 사용자이고 problem/studyroom 등 다른 도메인도 KST 기준으로 하루를 가른다.
        // JVM 기본 시간대를 쓰면 배포 환경에 따라 스트릭의 "오늘"이 하루 어긋난다.
        return getLearningCalendar(userId, year, month, LocalDate.now(KST));
    }

    LearningCalendarResponseDto getLearningCalendar(Long userId, int year, int month, LocalDate today) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(END_OF_DAY);

        Map<LocalDate, LearningCalendarQueryRepository.DailyReviewStat> reviewStats = toReviewStatMap(
                calendarRepository.findDailyReviewStats(userId, start, end)
        );
        Map<LocalDate, LearningCalendarQueryRepository.DailyNoteWriteStat> noteWriteStats = toNoteWriteStatMap(
                calendarRepository.findDailyNoteWriteStats(userId, start, end)
        );
        Map<LocalDate, List<String>> reviewedItems = toReviewedItemsMap(
                calendarRepository.findReviewItems(userId, start, end)
        );
        Map<LocalDate, String> moodEmojiKeys = moodRepository.findAllByUserIdAndStudyDateBetween(userId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(LearningCalendarMood::getStudyDate, LearningCalendarMood::getEmojiKey));

        List<LearningCalendarResponseDto.DailyStudyRecord> records = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> buildDailyRecord(
                        date,
                        reviewStats.get(date),
                        noteWriteStats.get(date),
                        reviewedItems.getOrDefault(date, List.of()),
                        moodEmojiKeys.get(date)
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

    @Transactional
    public LearningCalendarMoodResponseDto updateMood(Long userId, LearningCalendarMoodRequestDto request) {
        if (request == null || request.date() == null) {
            throw new ApplicationException(LearningCalendarErrorCase.INVALID_DATE_FORMAT);
        }
        customEmojiValidator.validate(request.emojiKey());
        if (!calendarRepository.existsStudyRecord(userId, request.date())) {
            throw new ApplicationException(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND);
        }
        Optional<LearningCalendarMood> existingMood = moodRepository.findByUserIdAndStudyDate(userId, request.date());
        LearningCalendarMood mood = existingMood
                .orElseGet(() -> moodRepository.save(LearningCalendarMood.create(userId, request.date(), request.emojiKey())));
        mood.updateEmojiKey(request.emojiKey());

        // 기분 미션은 "그 날짜에 처음 기분을 남겼을 때"만 오른다. 이모지를 바꿀 때마다 오르면
        // 버튼 한 번으로 주간 미션까지 채울 수 있다.
        if (existingMood.isEmpty()) {
            missionProgressUpdater.increase(userId, MissionMetric.MOOD_LOGGED);
        }

        return new LearningCalendarMoodResponseDto(mood.getStudyDate(), mood.getEmojiKey());
    }

    private LearningCalendarResponseDto.DailyStudyRecord buildDailyRecord(
            LocalDate date,
            LearningCalendarQueryRepository.DailyReviewStat reviewStat,
            LearningCalendarQueryRepository.DailyNoteWriteStat noteWriteStat,
            List<String> reviewedItems,
            String moodEmojiKey
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
                .moodEmojiKey(moodEmojiKey)
                .build();
    }

    private TreeSet<LocalDate> findStudyDates(Long userId) {
        return streakCacheService.get(userId).orElseGet(() -> {
            TreeSet<LocalDate> studyDates = new TreeSet<>();
            studyDates.addAll(calendarRepository.findDistinctReviewDatesTotal(userId));
            studyDates.addAll(calendarRepository.findDistinctNoteWriteDatesTotal(userId));
            streakCacheService.put(userId, studyDates);
            return studyDates;
        });
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
