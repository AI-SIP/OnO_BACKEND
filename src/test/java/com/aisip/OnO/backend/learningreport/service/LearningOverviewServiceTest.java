package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.learningcalendar.service.LearningCalendarService;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.NoteStatus;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.Previous;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.Summary;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.TrendBucket;
import com.aisip.OnO.backend.learningreport.dto.LearningOverviewResponseDto.WeakFolder;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod.MONTH;
import static com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod.TOTAL;
import static com.aisip.OnO.backend.learningreport.dto.LearningOverviewPeriod.WEEK;
import static com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.CORRECT;
import static com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.PARTIAL;
import static com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.UNKNOWN;
import static com.aisip.OnO.backend.problemsolve.entity.AnswerStatus.WRONG;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 학습 보고서 개요 집계. 기대값은 픽스처에서 손으로 계산해 정확히 비교한다.
 *
 * <p>오늘은 2026-10-08(목)로 고정한다. 이번 주는 10-05(월) ~ 10-11(일)이고 집계는 10-08 까지다.
 * 오답노트 작성일도 공부한 날로 잡히므로, 공부한 날을 세는 테스트가 아니면 문제는 2026-08-01 에 만든다.
 */
@DisplayName("LearningOverviewService - 학습 보고서 개요")
class LearningOverviewServiceTest extends LearningReportTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final LocalDateTime OLD_NOTE = LocalDateTime.of(2026, 8, 1, 8, 0);

    @Autowired
    private LearningCalendarService learningCalendarService;

    private Long userId;

    @BeforeEach
    void setUpUser() {
        userId = fixtures.createUser("overview").getId();
    }

    private LearningOverviewResponseDto overview(LearningOverviewPeriod period, LocalDate baseDate) {
        return learningOverviewService.getOverview(userId, period, baseDate, TODAY);
    }

    private static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 10, 0);
    }

    private static LocalDateTime at(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0);
    }

    private Problem oldNote() {
        return saveNoteWrittenAt(userId, OLD_NOTE);
    }

    private void solve(Problem problem, LocalDateTime practicedAt, AnswerStatus status) {
        saveSolve(userId, problem, practicedAt, status, null);
    }

    private static List<LocalDate> trendStarts(LearningOverviewResponseDto dto) {
        return dto.trend().stream().map(TrendBucket::startDate).toList();
    }

    private static List<LocalDate> trendEnds(LearningOverviewResponseDto dto) {
        return dto.trend().stream().map(TrendBucket::endDate).toList();
    }

    private static List<Long> trendCounts(LearningOverviewResponseDto dto) {
        return dto.trend().stream().map(TrendBucket::reviewCount).toList();
    }

    @Nested
    @DisplayName("기간")
    class Period {

        @Test
        @DisplayName("이번 주는 월요일부터 일요일까지이고 일요일이 미래여도 그대로 주며, 기록이 없으면 전부 0이다")
        void currentWeekEndsOnFutureSunday() {
            LearningOverviewResponseDto dto = overview(WEEK, TODAY);

            assertThat(dto.period()).isEqualTo(WEEK);
            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(dto.hasNext()).isFalse();
            assertThat(dto.hasPrevious()).isFalse();
            assertThat(dto.summary()).isEqualTo(new Summary(0, null, 0, 0));
            assertThat(dto.previous()).isEqualTo(new Previous(0, null, 0));
            assertThat(dto.noteStatus()).isEqualTo(new NoteStatus(0, 0, 0, 0, 0, 3));
            assertThat(dto.weakFolders()).isEmpty();
            assertThat(trendStarts(dto)).containsExactly(
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7),
                    LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10),
                    LocalDate.of(2026, 10, 11));
            assertThat(trendEnds(dto)).isEqualTo(trendStarts(dto));
            assertThat(trendCounts(dto)).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        @Test
        @DisplayName("일요일은 그 앞 월요일의 주에, 월요일은 새 주에 들어간다")
        void weekBoundaries() {
            LearningOverviewResponseDto sunday = overview(WEEK, LocalDate.of(2026, 10, 4));
            LearningOverviewResponseDto monday = overview(WEEK, LocalDate.of(2026, 10, 5));

            assertThat(sunday.startDate()).isEqualTo(LocalDate.of(2026, 9, 28));
            assertThat(sunday.endDate()).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(sunday.hasNext()).isTrue();
            assertThat(monday.startDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(monday.endDate()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(monday.hasNext()).isFalse();
        }

        @Test
        @DisplayName("오늘보다 뒤의 기준일은 오늘로 맞춘다")
        void futureBaseDateFallsBackToToday() {
            LearningOverviewResponseDto dto = overview(WEEK, LocalDate.of(2026, 12, 25));

            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(dto.hasNext()).isFalse();
        }

        @Test
        @DisplayName("지난달은 1일부터 말일이고, 막대는 월~일로 자른 주를 달 경계에서 자른다")
        void pastMonthWithWeekBuckets() {
            Problem problem = oldNote();
            solve(problem, at(9, 6), CORRECT);
            solve(problem, at(9, 7), CORRECT);
            solve(problem, at(9, 30, 23), WRONG);

            LearningOverviewResponseDto dto = overview(MONTH, LocalDate.of(2026, 9, 15));

            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(dto.hasNext()).isTrue();
            // 2026-09-01 은 화요일이다.
            assertThat(trendStarts(dto)).containsExactly(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28));
            assertThat(trendEnds(dto)).containsExactly(
                    LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 20),
                    LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 30));
            assertThat(trendCounts(dto)).containsExactly(1L, 1L, 0L, 0L, 1L);
        }

        @Test
        @DisplayName("이번 달 막대는 오늘 이후 주도 0으로 넣는다")
        void currentMonthIncludesFutureWeeks() {
            Problem problem = oldNote();
            solve(problem, at(10, 8), CORRECT);

            LearningOverviewResponseDto dto = overview(MONTH, null);

            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(dto.hasNext()).isFalse();
            assertThat(trendStarts(dto)).containsExactly(
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                    LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26));
            assertThat(trendEnds(dto)).containsExactly(
                    LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 18),
                    LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 31));
            assertThat(trendCounts(dto)).containsExactly(0L, 1L, 0L, 0L, 0L);
        }

        @Test
        @DisplayName("기간 시작보다 앞에 공부한 날이 있어야 이전 기간으로 갈 수 있다")
        void hasPreviousFollowsFirstStudyDate() {
            saveNoteWrittenAt(userId, at(10, 1));

            assertThat(overview(WEEK, TODAY).hasPrevious()).isTrue();
            assertThat(overview(WEEK, LocalDate.of(2026, 10, 1)).hasPrevious()).isFalse();
            assertThat(overview(MONTH, TODAY).hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("전체는 처음 공부한 날부터 오늘까지이고, 비교와 화살표가 없으며 막대는 최근 6개월이다")
        void totalPeriod() {
            Problem problem = saveNoteWrittenAt(userId, at(8, 3));
            solve(problem, at(9, 29), CORRECT);
            solve(problem, at(10, 8), WRONG);

            LearningOverviewResponseDto dto = overview(TOTAL, LocalDate.of(2026, 1, 1));

            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 8, 3));
            assertThat(dto.endDate()).isEqualTo(TODAY);
            assertThat(dto.hasPrevious()).isFalse();
            assertThat(dto.hasNext()).isFalse();
            assertThat(dto.previous()).isNull();
            assertThat(dto.summary()).isEqualTo(new Summary(2, 50.0, 3, 1));
            assertThat(trendStarts(dto)).containsExactly(
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
            assertThat(trendEnds(dto)).containsExactly(
                    LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 31),
                    LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 31));
            assertThat(trendCounts(dto)).containsExactly(0L, 0L, 0L, 0L, 1L, 1L);
        }

        @Test
        @DisplayName("공부한 적이 없으면 전체의 시작일은 null 이다")
        void totalWithoutStudy() {
            LearningOverviewResponseDto dto = overview(TOTAL, null);

            assertThat(dto.startDate()).isNull();
            assertThat(dto.endDate()).isEqualTo(TODAY);
            assertThat(dto.summary()).isEqualTo(new Summary(0, null, 0, 0));
            assertThat(dto.trend()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("지난 기간 비교")
    class Comparison {

        @Test
        @DisplayName("이번 주가 목요일까지만 지났으면 지난주도 월요일부터 목요일까지만 센다")
        void ongoingWeekComparesSameLength() {
            Problem problem = oldNote();
            solve(problem, at(9, 29), CORRECT);
            solve(problem, at(10, 1, 23), WRONG);
            solve(problem, at(10, 2), CORRECT);
            solve(problem, at(10, 3), CORRECT);
            solve(problem, at(10, 6), CORRECT);
            solve(problem, at(10, 8, 23), PARTIAL);

            LearningOverviewResponseDto dto = overview(WEEK, TODAY);

            assertThat(dto.summary().reviewCount()).isEqualTo(2);
            assertThat(dto.summary().accuracy()).isEqualTo(75.0);
            assertThat(dto.summary().studyDays()).isEqualTo(2);
            assertThat(dto.previous()).isEqualTo(new Previous(2, 50.0, 2));
        }

        @Test
        @DisplayName("이미 끝난 주를 보면 그 앞 주 전체와 비교한다")
        void finishedWeekComparesFullPreviousWeek() {
            Problem problem = oldNote();
            solve(problem, at(9, 21), CORRECT);
            solve(problem, at(9, 27, 23), WRONG);
            solve(problem, at(9, 29), CORRECT);
            solve(problem, at(10, 1), WRONG);
            solve(problem, at(10, 2), CORRECT);
            solve(problem, at(10, 4, 23), CORRECT);

            LearningOverviewResponseDto dto = overview(WEEK, LocalDate.of(2026, 10, 1));

            assertThat(dto.summary()).isEqualTo(new Summary(4, 75.0, 4, 0));
            assertThat(dto.previous()).isEqualTo(new Previous(2, 50.0, 2));
        }

        @Test
        @DisplayName("이번 달이 8일까지 지났으면 지난달도 1일부터 8일까지만 센다")
        void ongoingMonthComparesSameLength() {
            Problem problem = oldNote();
            solve(problem, at(9, 8, 23), CORRECT);
            solve(problem, at(9, 9), WRONG);
            solve(problem, at(10, 2), CORRECT);

            LearningOverviewResponseDto dto = overview(MONTH, TODAY);

            assertThat(dto.summary().reviewCount()).isEqualTo(1);
            assertThat(dto.previous()).isEqualTo(new Previous(1, 100.0, 1));
        }

        @Test
        @DisplayName("이번 달이 지난달보다 길면 지난달 말일에서 자른다")
        void longerMonthClipsAtPreviousMonthEnd() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 12, 1, 8, 0));
            solve(problem, LocalDateTime.of(2026, 1, 31, 10, 0), CORRECT);
            solve(problem, LocalDateTime.of(2026, 2, 28, 23, 0), WRONG);
            solve(problem, LocalDateTime.of(2026, 3, 1, 10, 0), CORRECT);

            LearningOverviewResponseDto dto = learningOverviewService.getOverview(
                    userId, MONTH, null, LocalDate.of(2026, 3, 31));

            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(dto.summary().reviewCount()).isEqualTo(1);
            assertThat(dto.previous()).isEqualTo(new Previous(1, 0.0, 1));
        }
    }

    @Nested
    @DisplayName("정답률")
    class Accuracy {

        @Test
        @DisplayName("UNKNOWN 은 분자 분모 모두에서 빼고 부분 정답은 0.5로 센다. 복습 수에는 넣는다")
        void excludesUnknown() {
            Problem problem = oldNote();
            solve(problem, at(10, 5), CORRECT);
            solve(problem, at(10, 5, 11), PARTIAL);
            solve(problem, at(10, 6), WRONG);
            solve(problem, at(10, 7), UNKNOWN);

            Summary summary = overview(WEEK, TODAY).summary();

            assertThat(summary.reviewCount()).isEqualTo(4);
            assertThat(summary.accuracy()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("소수 첫째 자리에서 반올림한다")
        void roundsToOneDecimal() {
            Problem problem = oldNote();
            solve(problem, at(10, 5), CORRECT);
            solve(problem, at(10, 6), CORRECT);
            solve(problem, at(10, 7), WRONG);

            assertThat(overview(WEEK, TODAY).summary().accuracy()).isEqualTo(66.7);
        }

        @Test
        @DisplayName("UNKNOWN 기록만 있으면 분모가 0이라 null 이다")
        void nullWhenOnlyUnknown() {
            Problem problem = oldNote();
            solve(problem, at(10, 5), UNKNOWN);

            Summary summary = overview(WEEK, TODAY).summary();

            assertThat(summary.reviewCount()).isEqualTo(1);
            assertThat(summary.accuracy()).isNull();
        }
    }

    @Nested
    @DisplayName("오답노트 세 단계")
    class NoteStages {

        /**
         * <pre>
         * known1     10-01 정답 2번, 10-03, 10-06 정답    -> 정답 날 3일, 10-06 졸업 (이번 주)
         * sameDay    10-05 정답 2번, 10-06 정답           -> 정답 날 2일, 헷갈림
         * relapsed   09-20, 09-22, 09-24 정답, 10-06 오답 -> 졸업 후 오답, 헷갈림
         * unknown    10-06 UNKNOWN 만                      -> 헷갈림
         * unsolved   기록 없음                             -> 안 풀어봄
         * knownOld   09-10, 09-12, 09-14 정답             -> 09-14 졸업 (이번 주 아님)
         * deleted    10-05, 10-06, 10-07 정답 후 삭제      -> 어디에도 안 셈
         * slipped    10-01, 10-03, 10-05 정답, 10-07 오답 -> 이번 주에 졸업했다가 다시 틀림, 헷갈림
         * partial    09-01, 09-02 정답, 09-03 부분, 09-04 정답 -> 부분 정답이 비워서 1일, 헷갈림
         * relearned  09-01 정답, 09-02 오답, 09-28, 10-02, 10-07 정답 -> 10-07 다시 졸업 (이번 주)
         * </pre>
         */
        @BeforeEach
        void setUpNotes() {
            Problem known1 = oldNote();
            solve(known1, at(10, 1, 9), CORRECT);
            solve(known1, at(10, 1, 15), CORRECT);
            solve(known1, at(10, 3), CORRECT);
            solve(known1, at(10, 6), CORRECT);

            Problem sameDay = oldNote();
            solve(sameDay, at(10, 5, 9), CORRECT);
            solve(sameDay, at(10, 5, 15), CORRECT);
            solve(sameDay, at(10, 6), CORRECT);

            Problem relapsed = oldNote();
            solve(relapsed, at(9, 20), CORRECT);
            solve(relapsed, at(9, 22), CORRECT);
            solve(relapsed, at(9, 24), CORRECT);
            solve(relapsed, at(10, 6), WRONG);

            Problem unknown = oldNote();
            solve(unknown, at(10, 6), UNKNOWN);

            oldNote();

            Problem knownOld = oldNote();
            solve(knownOld, at(9, 10), CORRECT);
            solve(knownOld, at(9, 12), CORRECT);
            solve(knownOld, at(9, 14), CORRECT);

            Problem deleted = oldNote();
            solve(deleted, at(10, 5), CORRECT);
            solve(deleted, at(10, 6), CORRECT);
            solve(deleted, at(10, 7), CORRECT);
            softDeleteNote(deleted);

            Problem slipped = oldNote();
            solve(slipped, at(10, 1), CORRECT);
            solve(slipped, at(10, 3), CORRECT);
            solve(slipped, at(10, 5), CORRECT);
            solve(slipped, at(10, 7), WRONG);

            Problem partial = oldNote();
            solve(partial, at(9, 1), CORRECT);
            solve(partial, at(9, 2), CORRECT);
            solve(partial, at(9, 3), PARTIAL);
            solve(partial, at(9, 4), CORRECT);

            Problem relearned = oldNote();
            solve(relearned, at(9, 1), CORRECT);
            solve(relearned, at(9, 2), WRONG);
            solve(relearned, at(9, 28), CORRECT);
            solve(relearned, at(10, 2), CORRECT);
            solve(relearned, at(10, 7), CORRECT);
        }

        @Test
        @DisplayName("이번 주: 아는 3, 헷갈림 5, 안 풀어봄 1, 이번 주에 졸업 2")
        void currentWeek() {
            assertThat(overview(WEEK, TODAY).noteStatus()).isEqualTo(new NoteStatus(9, 3, 5, 1, 2, 3));
        }

        @Test
        @DisplayName("지난 주를 봐도 상태는 오늘 기준이고, 새로 알게 된 수만 그 주 기준이다")
        void previousWeekKeepsTodaySnapshot() {
            assertThat(overview(WEEK, LocalDate.of(2026, 10, 1)).noteStatus())
                    .isEqualTo(new NoteStatus(9, 3, 5, 1, 0, 3));
        }

        @Test
        @DisplayName("9월에 졸업한 문제는 9월 보고서의 새로 알게 된 수에 들어간다")
        void septemberNewlyKnown() {
            assertThat(overview(MONTH, LocalDate.of(2026, 9, 15)).noteStatus().newlyKnownCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("전체에서는 지금 아는 문제가 전부 새로 알게 된 문제다")
        void totalNewlyKnownEqualsKnown() {
            assertThat(overview(TOTAL, null).noteStatus()).isEqualTo(new NoteStatus(9, 3, 5, 1, 3, 3));
        }
    }

    @Nested
    @DisplayName("삭제된 문제")
    class DeletedProblem {

        @Test
        @DisplayName("지운 문제의 풀이 기록은 복습 수, 정답률, 막대, 폴더, 오답노트 상태에서 모두 빠진다")
        void excludesDeletedProblemEverywhere() {
            Folder root = fixtures.createFolder(userId, "책장", null);
            Problem live = saveNoteInFolder(userId, root, OLD_NOTE);
            Problem deleted = saveNoteInFolder(userId, root, OLD_NOTE);
            solve(live, at(10, 6, 9), CORRECT);
            solve(deleted, at(10, 6, 10), WRONG);
            solve(deleted, at(10, 6, 11), WRONG);
            softDeleteNote(deleted);

            LearningOverviewResponseDto dto = overview(WEEK, TODAY);

            assertThat(dto.summary()).isEqualTo(new Summary(1, 100.0, 1, 0));
            assertThat(trendCounts(dto)).containsExactly(0L, 1L, 0L, 0L, 0L, 0L, 0L);
            assertThat(dto.weakFolders()).isEmpty();
            assertThat(dto.noteStatus()).isEqualTo(new NoteStatus(1, 0, 1, 0, 0, 3));
        }
    }

    @Nested
    @DisplayName("자주 틀린 폴더")
    class WeakFolders {

        @Test
        @DisplayName("정답률 낮은 순, 같으면 오답 많은 순, 그다음 폴더 id 순으로 3개만 주고 오답 없는 폴더는 뺀다")
        void sortsAndLimits() {
            Folder root = fixtures.createFolder(userId, "책장", null);
            Folder a = fixtures.createFolder(userId, "A", root);
            Folder a2 = fixtures.createFolder(userId, "A2", root);
            Folder b = fixtures.createFolder(userId, "B", root);
            Folder c = fixtures.createFolder(userId, "C", root);
            Folder d = fixtures.createFolder(userId, "D", root);

            Problem inRoot = saveNoteInFolder(userId, root, OLD_NOTE);
            solve(inRoot, at(10, 5, 9), WRONG);
            solve(inRoot, at(10, 5, 10), WRONG);
            solve(inRoot, at(10, 6, 9), CORRECT);
            solve(inRoot, at(10, 6, 10), CORRECT);

            Problem inA = saveNoteInFolder(userId, a, OLD_NOTE);
            solve(inA, at(10, 5), WRONG);
            solve(inA, at(10, 6), CORRECT);

            Problem inA2 = saveNoteInFolder(userId, a2, OLD_NOTE);
            solve(inA2, at(10, 5), WRONG);
            solve(inA2, at(10, 6), CORRECT);

            Problem inB = saveNoteInFolder(userId, b, OLD_NOTE);
            solve(inB, at(10, 5), WRONG);
            solve(inB, at(10, 6), PARTIAL);
            solve(inB, at(10, 7), UNKNOWN);

            Problem inC = saveNoteInFolder(userId, c, OLD_NOTE);
            solve(inC, at(9, 20), WRONG);
            solve(inC, at(10, 5), CORRECT);
            solve(inC, at(10, 6), CORRECT);
            solve(inC, at(10, 7), CORRECT);

            Problem inD = saveNoteInFolder(userId, d, OLD_NOTE);
            solve(inD, at(10, 5), WRONG);
            solve(inD, at(10, 6), CORRECT);
            solve(inD, at(10, 7), CORRECT);
            solve(inD, at(10, 8), CORRECT);

            List<WeakFolder> weekly = overview(WEEK, TODAY).weakFolders();

            assertThat(weekly).containsExactly(
                    new WeakFolder(b.getId(), "B", 3, 1, 25.0),
                    new WeakFolder(root.getId(), "책장", 4, 2, 50.0),
                    new WeakFolder(a.getId(), "A", 2, 1, 50.0));
        }

        @Test
        @DisplayName("전체는 기간과 상관없이 전체 기록으로 묶는다")
        void totalUsesAllRecords() {
            Folder root = fixtures.createFolder(userId, "책장", null);
            Problem problem = saveNoteInFolder(userId, root, OLD_NOTE);
            solve(problem, at(8, 10), WRONG);
            solve(problem, at(10, 6), CORRECT);

            assertThat(overview(WEEK, TODAY).weakFolders()).isEmpty();
            assertThat(overview(TOTAL, null).weakFolders())
                    .containsExactly(new WeakFolder(root.getId(), "책장", 2, 1, 50.0));
        }
    }

    @Nested
    @DisplayName("연속 공부일")
    class Streak {

        @Test
        @DisplayName("학습 달력의 연속일과 같은 값이다 (오늘 안 했으면 어제부터 센다)")
        void matchesLearningCalendar() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            Problem problem = saveNoteWrittenAt(userId, today.minusDays(4).atTime(9, 0));
            solve(problem, today.minusDays(2).atTime(9, 0), CORRECT);
            solve(problem, today.minusDays(1).atTime(9, 0), WRONG);

            int overviewStreak = learningOverviewService.getOverview(userId, WEEK, null).summary().currentStreak();
            int calendarStreak = learningCalendarService
                    .getLearningCalendar(userId, today.getYear(), today.getMonthValue())
                    .currentStreak();

            assertThat(overviewStreak).isEqualTo(2);
            assertThat(calendarStreak).isEqualTo(overviewStreak);
        }
    }
}
