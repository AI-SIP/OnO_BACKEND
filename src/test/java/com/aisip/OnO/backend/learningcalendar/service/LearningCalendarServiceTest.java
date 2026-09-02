package com.aisip.OnO.backend.learningcalendar.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodRequestDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarMoodResponseDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto;
import com.aisip.OnO.backend.learningcalendar.dto.LearningCalendarResponseDto.DailyStudyRecord;
import com.aisip.OnO.backend.learningcalendar.exception.LearningCalendarErrorCase;
import com.aisip.OnO.backend.learningcalendar.support.LearningCalendarTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LearningCalendarService")
class LearningCalendarServiceTest extends LearningCalendarTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private LearningCalendarService learningCalendarService;

    private User user;
    private Long userId;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser("calendar");
        userId = user.getId();
    }

    @Nested
    @DisplayName("월별 조회")
    class MonthlyQuery {

        @Test
        @DisplayName("기록이 하나도 없으면 그 달의 일수만큼 0 기반 레코드를 만든다")
        void returnsZeroFilledRecordsWhenNoData() {
            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 2, LocalDate.of(2026, 2, 6));

            assertThat(response.year()).isEqualTo(2026);
            assertThat(response.month()).isEqualTo(2);
            assertThat(response.records()).as("2026년 2월은 평년이라 28일").hasSize(28);
            assertThat(response.currentStreak()).isZero();
            assertThat(response.bestStreak()).isZero();
            assertThat(response.thisMonthStudyDays()).isZero();
            assertThat(response.records()).allSatisfy(record -> {
                assertThat(record.hasStudied()).isFalse();
                assertThat(record.reviewCount()).isZero();
                assertThat(record.noteWriteCount()).isZero();
                assertThat(record.studyMinutes()).isZero();
                assertThat(record.reviewedItems()).isEmpty();
                assertThat(record.moodEmojiKey()).isNull();
            });
        }

        @Test
        @DisplayName("윤년 2월은 29일치 레코드를 만든다")
        void leapFebruaryHas29Records() {
            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2024, 2, LocalDate.of(2024, 3, 1));

            assertThat(response.records()).hasSize(29);
            assertThat(response.records().get(28).date()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("일부 날짜에만 기록이 있으면 그 날짜의 집계만 채워진다")
        void aggregatesOnlyRecordedDays() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 2, 8, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 1, 9, 0), 600);
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 1, 10, 0), 120);
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 3, 9, 0), 300);

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 10));
            Map<LocalDate, DailyStudyRecord> records = recordsByDate(response);

            assertThat(response.records()).hasSize(31);
            assertThat(response.thisMonthStudyDays()).as("5/1, 5/2, 5/3 세 날짜").isEqualTo(3);

            DailyStudyRecord may1 = records.get(LocalDate.of(2026, 5, 1));
            assertThat(may1.hasStudied()).isTrue();
            assertThat(may1.reviewCount()).isEqualTo(2);
            assertThat(may1.noteWriteCount()).isEqualTo(1);
            assertThat(may1.studyMinutes()).as("600초 + 120초 = 720초 = 12분").isEqualTo(12);

            DailyStudyRecord may2 = records.get(LocalDate.of(2026, 5, 2));
            assertThat(may2.hasStudied()).as("복습이 없어도 오답노트 작성만으로 학습일").isTrue();
            assertThat(may2.reviewCount()).isZero();
            assertThat(may2.noteWriteCount()).isEqualTo(1);
            assertThat(may2.studyMinutes()).isZero();

            DailyStudyRecord may3 = records.get(LocalDate.of(2026, 5, 3));
            assertThat(may3.hasStudied()).as("오답노트 작성이 없어도 복습만으로 학습일").isTrue();
            assertThat(may3.reviewCount()).isEqualTo(1);
            assertThat(may3.noteWriteCount()).isZero();
            assertThat(may3.studyMinutes()).isEqualTo(5);

            assertThat(records.get(LocalDate.of(2026, 5, 4)).hasStudied()).isFalse();
        }

        @Test
        @DisplayName("한 달 전체에 기록이 있으면 학습일수와 최고 연속일수가 그 달의 일수와 같다")
        void fullMonthOfRecords() {
            for (int day = 1; day <= 30; day++) {
                saveNoteWrittenAt(userId, LocalDateTime.of(2026, 4, day, 8, 0));
            }

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 4, LocalDate.of(2026, 4, 30));

            assertThat(response.thisMonthStudyDays()).isEqualTo(30);
            assertThat(response.bestStreak()).isEqualTo(30);
            assertThat(response.records()).allMatch(DailyStudyRecord::hasStudied);
        }

        @Test
        @DisplayName("다른 달 기록은 이번 달 레코드에 섞이지 않는다")
        void otherMonthRecordsAreExcluded() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 4, 30, 8, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 6, 1, 8, 0), 60);
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 15, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 20));

            assertThat(response.thisMonthStudyDays()).isEqualTo(1);
            assertThat(recordsByDate(response).get(LocalDate.of(2026, 5, 15)).noteWriteCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("초 단위 학습시간은 분 단위로 내림한다")
        void studyMinutesFloorsSeconds() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 1, 9, 0), 59);
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 2, 9, 0), 119);

            Map<LocalDate, DailyStudyRecord> records = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 10)));

            assertThat(records.get(LocalDate.of(2026, 5, 1)).studyMinutes()).as("59초는 0분").isZero();
            assertThat(records.get(LocalDate.of(2026, 5, 2)).studyMinutes()).as("119초는 1분").isEqualTo(1);
        }

        @ParameterizedTest(name = "month={0}")
        @ValueSource(ints = {0, 13, -1})
        @DisplayName("존재하지 않는 월은 예외가 된다")
        void invalidMonthThrows(int month) {
            assertThatThrownBy(() ->
                    learningCalendarService.getLearningCalendar(userId, 2026, month, LocalDate.of(2026, 5, 1)))
                    .isInstanceOf(DateTimeException.class);
        }

        @Test
        @DisplayName("표현 가능한 범위를 벗어난 연도는 예외가 된다")
        void invalidYearThrows() {
            assertThatThrownBy(() ->
                    learningCalendarService.getLearningCalendar(userId, 1_000_000_000, 5, LocalDate.of(2026, 5, 1)))
                    .isInstanceOf(DateTimeException.class);
        }
    }

    @Nested
    @DisplayName("복습 항목 목록")
    class ReviewedItems {

        @Test
        @DisplayName("출처가 있으면 출처를, 없으면 메모를, 둘 다 없으면 문제 번호를 제목으로 쓴다")
        void titleFallbackChain() {
            LocalDateTime day = LocalDateTime.of(2026, 5, 1, 9, 0);
            Problem withReference = saveNoteWrittenAt(userId, day, "메모A", "출처A");
            Problem withMemoOnly = saveNoteWrittenAt(userId, day, "메모B", "  ");
            Problem withNeither = saveNoteWrittenAt(userId, day, null, null);

            saveSolveAt(userId, withReference, day.plusHours(1), 60);
            saveSolveAt(userId, withMemoOnly, day.plusHours(2), 60);
            saveSolveAt(userId, withNeither, day.plusHours(3), 60);

            DailyStudyRecord record = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 10)))
                    .get(LocalDate.of(2026, 5, 1));

            assertThat(record.reviewedItems())
                    .as("같은 날 복습은 최근 순으로 정렬된다")
                    .containsExactly("문제 " + withNeither.getId(), "메모B", "출처A");
        }

        @Test
        @DisplayName("같은 제목은 한 번만 담고 하루 최대 10개까지만 담는다")
        void dedupesAndCapsAtTen() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            Problem duplicated = saveNoteWrittenAt(userId, date.atTime(7, 0), "메모", "중복 출처");
            saveSolveAt(userId, duplicated, date.atTime(8, 0), 60);
            saveSolveAt(userId, duplicated, date.atTime(8, 30), 60);

            for (int i = 0; i < 12; i++) {
                Problem problem = saveNoteWrittenAt(userId, date.atTime(7, 0), "메모" + i, "출처" + i);
                saveSolveAt(userId, problem, date.atTime(9, 0).plusMinutes(i), 60);
            }

            DailyStudyRecord record = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 10)))
                    .get(date);

            assertThat(record.reviewCount()).as("복습 건수 자체는 잘리지 않는다").isEqualTo(14);
            assertThat(record.reviewedItems()).hasSize(10);
            assertThat(record.reviewedItems()).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("스트릭 계산")
    class StreakCalculation {

        @Test
        @DisplayName("오늘 기록이 있으면 오늘을 포함해 연속일수를 센다")
        void includesTodayWhenStudiedToday() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 3, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 4, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 5, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 5));

            assertThat(response.currentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("오늘 기록이 없어도 어제까지 이어졌다면 연속으로 인정한다")
        void countsFromYesterdayWhenTodayIsEmpty() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 3, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 4, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 5));

            assertThat(response.currentStreak()).isEqualTo(2);
        }

        @Test
        @DisplayName("오늘도 어제도 기록이 없으면 연속일수는 0이다")
        void breaksWhenTodayAndYesterdayAreEmpty() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 2, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 3, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 5));

            assertThat(response.currentStreak()).isZero();
        }

        @Test
        @DisplayName("하루라도 건너뛰면 그 이전 기록은 연속에 포함하지 않는다")
        void gapBreaksStreak() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 2, 8, 0));
            // 5/3 건너뜀
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 4, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 5, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 5));

            assertThat(response.currentStreak()).isEqualTo(2);
        }

        @Test
        @DisplayName("월 경계를 넘는 연속 기록도 하나의 연속으로 센다")
        void streakSpansMonthBoundary() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 4, 29, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 4, 30, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 2, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 2));

            assertThat(response.currentStreak()).as("4/29~5/2 나흘").isEqualTo(4);
            assertThat(response.bestStreak()).as("bestStreak 은 조회한 달 안에서만 계산한다").isEqualTo(2);
        }

        @Test
        @DisplayName("윤년 2월 29일을 사이에 둔 연속 기록이 끊기지 않는다")
        void streakCrossesLeapDay() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2024, 2, 28, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2024, 2, 29, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2024, 3, 1, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2024, 2, LocalDate.of(2024, 3, 1));

            assertThat(response.currentStreak()).isEqualTo(3);
            assertThat(response.bestStreak()).as("2월 안에서는 28·29일 이틀").isEqualTo(2);
        }

        @Test
        @DisplayName("평년 2월 28일과 3월 1일은 연속이지만 2월 29일을 기대하면 끊긴다")
        void nonLeapFebruaryIsContinuousWithMarch() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 28, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 3, 1, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 2, LocalDate.of(2026, 3, 1));

            assertThat(response.currentStreak()).isEqualTo(2);
        }

        @Test
        @DisplayName("최고 연속일수는 조회한 달 안에서 가장 긴 구간이다")
        void bestStreakIsLongestRunInMonth() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 2, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 10, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 11, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 12, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 13, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 20, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 25));

            assertThat(response.bestStreak()).as("5/10~5/13 나흘이 최장").isEqualTo(4);
            assertThat(response.currentStreak()).isZero();
        }

        @Test
        @DisplayName("하루만 기록이 있어도 최고 연속일수는 1이다")
        void bestStreakIsOneForSingleDay() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 7, 8, 0));

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 25));

            assertThat(response.bestStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("복습 기록만으로도 연속일수가 이어진다")
        void solvesAloneKeepStreak() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 1, 1, 8, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 3, 9, 0), 60);
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 4, 9, 0), 60);

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 4));

            assertThat(response.currentStreak()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("하루 경계")
    class DayBoundary {

        @Test
        @DisplayName("자정 정각 기록은 그날에, 전날 23:59:59 기록은 전날에 집계한다")
        void midnightBelongsToTheNewDay() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 1, 0, 0, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 1, 23, 59, 59), 60);
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 2, 0, 0, 0), 60);

            Map<LocalDate, DailyStudyRecord> records = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 10)));

            assertThat(records.get(LocalDate.of(2026, 5, 1)).noteWriteCount()).isEqualTo(1);
            assertThat(records.get(LocalDate.of(2026, 5, 1)).reviewCount()).isEqualTo(1);
            assertThat(records.get(LocalDate.of(2026, 5, 2)).reviewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("월 마지막 날 23:59:59.999999 기록도 그 달에 포함한다")
        void lastMicrosecondOfMonthIsIncluded() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 5, 31, 23, 59, 59, 999_999_000));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 5, 31, 23, 59, 59, 999_999_000), 120);

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 6, 1));
            DailyStudyRecord lastDay = recordsByDate(response).get(LocalDate.of(2026, 5, 31));

            assertThat(lastDay.noteWriteCount()).as("월 마지막 순간의 오답노트 작성").isEqualTo(1);
            assertThat(lastDay.reviewCount()).as("월 마지막 순간의 복습").isEqualTo(1);
            assertThat(response.thisMonthStudyDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("다음 달 첫날 자정 기록은 이번 달에 포함하지 않는다")
        void firstMomentOfNextMonthIsExcluded() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 1, 0, 0, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 6, 1, 0, 0, 0), 60);

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 6, 1));

            assertThat(response.thisMonthStudyDays()).isZero();
        }

        @Test
        @DisplayName("JVM 기본 시간대가 달라도 Asia/Seoul 기준 오늘로 연속일수를 계산한다")
        void currentStreakUsesSeoulToday() {
            LocalDate seoulToday = LocalDate.now(KST);
            Problem note = saveNoteWrittenAt(userId, seoulToday.atTime(12, 0));
            saveSolveAt(userId, note, seoulToday.plusDays(1).atTime(12, 0), 60);

            TimeZone originalTimeZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneWithDifferentDateThan(seoulToday)));

                LearningCalendarResponseDto response = learningCalendarService.getLearningCalendar(
                        userId, seoulToday.getYear(), seoulToday.getMonthValue());

                assertThat(response.currentStreak())
                        .as("서울 기준 오늘 하루만 연속. 하루 뒤 기록은 아직 연속에 들어가면 안 된다")
                        .isEqualTo(1);
            } finally {
                TimeZone.setDefault(originalTimeZone);
            }
        }

        /**
         * UTC+14 와 UTC-12 는 26시간 차이라 둘 중 최소 하나는 서울과 날짜가 다르다.
         * 실행 시각과 무관하게 "기본 시간대가 서울과 어긋난 상황"을 만들기 위한 선택이다.
         */
        private ZoneId zoneWithDifferentDateThan(LocalDate seoulToday) {
            return List.of(ZoneId.of("Etc/GMT-14"), ZoneId.of("Etc/GMT+12")).stream()
                    .filter(zone -> !LocalDate.now(zone).equals(seoulToday))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("서울과 날짜가 다른 시간대를 찾지 못했다"));
        }
    }

    @Nested
    @DisplayName("감정 이모지")
    class Mood {

        @Test
        @DisplayName("학습 기록이 있는 날짜에 이모지를 저장하면 달력 조회에 반영된다")
        void savesMoodOnStudiedDate() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 7, 8, 0));

            LearningCalendarMoodResponseDto saved = learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "happy_tears"));

            assertThat(saved.date()).isEqualTo(LocalDate.of(2026, 6, 7));
            assertThat(saved.emojiKey()).isEqualTo("happy_tears");

            Map<LocalDate, DailyStudyRecord> records = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 6, LocalDate.of(2026, 6, 8)));
            assertThat(records.get(LocalDate.of(2026, 6, 7)).moodEmojiKey()).isEqualTo("happy_tears");
            assertThat(records.get(LocalDate.of(2026, 6, 8)).moodEmojiKey()).isNull();
        }

        @Test
        @DisplayName("복습 기록만 있는 날짜에도 이모지를 저장할 수 있다")
        void savesMoodOnSolveOnlyDate() {
            Problem note = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 1, 8, 0));
            saveSolveAt(userId, note, LocalDateTime.of(2026, 6, 7, 9, 0), 60);

            LearningCalendarMoodResponseDto saved = learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "cool_sunglasses"));

            assertThat(saved.emojiKey()).isEqualTo("cool_sunglasses");
        }

        @Test
        @DisplayName("같은 날짜에 다시 저장하면 행을 늘리지 않고 덮어쓴다")
        void overwritesExistingMood() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 7, 8, 0));

            learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "happy_tears"));
            LearningCalendarMoodResponseDto updated = learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "success_checkmark"));

            assertThat(updated.emojiKey()).isEqualTo("success_checkmark");
            assertThat(moodRepository.findAll())
                    .as("유니크 제약(user_id, study_date) 때문에 행은 하나여야 한다")
                    .hasSize(1);
            assertThat(moodRepository.findByUserIdAndStudyDate(userId, LocalDate.of(2026, 6, 7)))
                    .get()
                    .satisfies(mood -> assertThat(mood.getEmojiKey()).isEqualTo("success_checkmark"));
        }

        @Test
        @DisplayName("학습 기록이 없는 날짜에는 이모지를 저장할 수 없다")
        void rejectsMoodOnDateWithoutRecord() {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 6, 8, 0));

            assertThatThrownBy(() -> learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), "happy_tears")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND.getErrorCode());
        }

        @Test
        @DisplayName("날짜가 비어 있으면 날짜 형식 오류가 된다")
        void rejectsNullDate() {
            assertThatThrownBy(() -> learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(null, "happy_tears")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(LearningCalendarErrorCase.INVALID_DATE_FORMAT.getErrorCode());
        }

        @Test
        @DisplayName("요청 자체가 비어 있으면 날짜 형식 오류가 된다")
        void rejectsNullRequest() {
            assertThatThrownBy(() -> learningCalendarService.updateMood(userId, null))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(LearningCalendarErrorCase.INVALID_DATE_FORMAT.getErrorCode());
        }

        @ParameterizedTest(name = "emojiKey=\"{0}\"")
        @ValueSource(strings = {"not_an_emoji", "HAPPY_TEARS", " happy_tears", ""})
        @DisplayName("허용 목록에 없는 이모지 키는 거부한다")
        void rejectsUnknownEmojiKey(String emojiKey) {
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 6, 7, 8, 0));

            assertThatThrownBy(() -> learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), emojiKey)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(CustomEmojiErrorCase.INVALID_EMOJI_KEY.getErrorCode());
        }

        @Test
        @DisplayName("이모지 키가 비어 있으면 학습 기록 존재 여부보다 먼저 거부한다")
        void validatesEmojiBeforeRecordExistence() {
            assertThatThrownBy(() -> learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(LocalDate.of(2026, 6, 7), null)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(CustomEmojiErrorCase.INVALID_EMOJI_KEY.getErrorCode());
        }
    }

    @Nested
    @DisplayName("사용자 격리")
    class Ownership {

        @Test
        @DisplayName("다른 사용자의 학습 기록은 내 달력에 나타나지 않는다")
        void otherUsersRecordsAreInvisible() {
            User other = fixtures.createOtherUser();
            Problem otherNote = saveNoteWrittenAt(other.getId(), LocalDateTime.of(2026, 5, 1, 8, 0));
            saveSolveAt(other.getId(), otherNote, LocalDateTime.of(2026, 5, 1, 9, 0), 999);

            LearningCalendarResponseDto response =
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 2));

            assertThat(response.thisMonthStudyDays()).isZero();
            assertThat(response.currentStreak()).isZero();
            assertThat(recordsByDate(response).get(LocalDate.of(2026, 5, 1)).studyMinutes()).isZero();
        }

        @Test
        @DisplayName("다른 사용자의 감정 이모지는 내 달력에 보이지 않는다")
        void otherUsersMoodIsInvisible() {
            User other = fixtures.createOtherUser();
            LocalDate date = LocalDate.of(2026, 5, 1);
            saveNoteWrittenAt(other.getId(), date.atTime(8, 0));
            saveNoteWrittenAt(userId, date.atTime(8, 0));

            learningCalendarService.updateMood(other.getId(),
                    new LearningCalendarMoodRequestDto(date, "stressed_bomb"));

            Map<LocalDate, DailyStudyRecord> mine = recordsByDate(
                    learningCalendarService.getLearningCalendar(userId, 2026, 5, LocalDate.of(2026, 5, 2)));
            assertThat(mine.get(date).moodEmojiKey()).isNull();

            learningCalendarService.updateMood(userId, new LearningCalendarMoodRequestDto(date, "gold_medal"));

            Map<LocalDate, DailyStudyRecord> theirs = recordsByDate(
                    learningCalendarService.getLearningCalendar(other.getId(), 2026, 5, LocalDate.of(2026, 5, 2)));
            assertThat(theirs.get(date).moodEmojiKey())
                    .as("내가 저장해도 상대의 이모지는 바뀌지 않는다")
                    .isEqualTo("stressed_bomb");
        }

        @Test
        @DisplayName("다른 사용자만 학습한 날짜에는 내 이모지를 저장할 수 없다")
        void cannotSaveMoodOnAnotherUsersStudyDate() {
            User other = fixtures.createOtherUser();
            LocalDate date = LocalDate.of(2026, 5, 1);
            saveNoteWrittenAt(other.getId(), date.atTime(8, 0));

            assertThatThrownBy(() -> learningCalendarService.updateMood(userId,
                    new LearningCalendarMoodRequestDto(date, "happy_tears")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCase().getErrorCode())
                    .isEqualTo(LearningCalendarErrorCase.CALENDAR_RECORD_NOT_FOUND.getErrorCode());
        }
    }

    @AfterEach
    void evictStreak() {
        if (userId != null) {
            evictStreakCache(userId);
        }
    }
}
