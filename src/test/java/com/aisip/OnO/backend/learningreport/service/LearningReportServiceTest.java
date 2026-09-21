package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningComparison;
import com.aisip.OnO.backend.learningreport.dto.LearningPeriodReport;
import com.aisip.OnO.backend.learningreport.dto.LearningReportResponseDto;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

/**
 * 리포트의 집계 값은 픽스처로부터 기대값을 손으로 계산해 정확히 비교한다.
 * "0이 아니다" 류의 단언은 집계 로직이 틀려도 통과하므로 쓰지 않는다.
 */
@DisplayName("LearningReportService - 집계")
class LearningReportServiceTest extends LearningReportTestSupport {

    /**
     * 기준일. 이 값으로부터 서비스가 만드는 구간은 다음과 같다.
     * <pre>
     * WEEKLY           2026-02-15 ~ 2026-02-21 (기준일 포함 7일)
     * PREVIOUS_WEEKLY  2026-02-08 ~ 2026-02-14
     * MONTHLY          2026-01-25 ~ 2026-02-21 (기준일 포함 28일)
     * PREVIOUS_MONTHLY 2025-12-28 ~ 2026-01-24
     * </pre>
     */
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 2, 21);

    private User user;
    private Long userId;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser("report");
        userId = user.getId();
    }

    @Nested
    @DisplayName("데이터가 없을 때")
    class EmptyData {

        @Test
        @DisplayName("모든 집계가 0이고 추이 버킷만 기간 길이만큼 남는다")
        void returnsZeroBasedReport() {
            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            for (LearningPeriodReport period : List.of(report.weekly(), report.monthly(), report.total())) {
                assertThat(period.reviewCount()).as(period.periodLabel() + " 복습 수").isZero();
                assertThat(period.noteWriteCount()).as(period.periodLabel() + " 오답노트 작성 수").isZero();
                assertThat(period.notePracticeCount()).as(period.periodLabel() + " 복습노트 사용 수").isZero();
                assertThat(period.averageAccuracy()).as(period.periodLabel() + " 정답률").isEqualTo(0.0);
                assertThat(period.averageStudyTimeMinutes()).as(period.periodLabel() + " 학습 시간").isEqualTo(0.0);
                assertThat(period.consecutiveLearningDays()).as(period.periodLabel() + " 연속 학습일").isZero();
                assertThat(period.weakAreas()).as(period.periodLabel() + " 취약 유형").isEmpty();
            }

            assertThat(report.weekly().trend()).as("주간은 하루 단위 7개 버킷").hasSize(7);
            assertThat(report.monthly().trend()).as("월간은 주 단위 4개 버킷").hasSize(4);
            assertThat(report.total().trend()).as("누적은 최근 6개월 버킷").hasSize(6);
            assertThat(trendMap(report.weekly().trend()).values()).containsOnly(0L);
            assertThat(trendMap(report.monthly().trend()).values()).containsOnly(0L);
            assertThat(trendMap(report.total().trend()).values()).containsOnly(0L);
        }

        @Test
        @DisplayName("이전 기간도 0이면 변화율은 100%가 아니라 0%다")
        void comparisonIsZeroWhenBothPeriodsAreEmpty() {
            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            LearningComparison weekly = report.weeklyComparison();
            assertThat(weekly.basePeriod()).isEqualTo("WEEKLY");
            assertThat(weekly.compareTo()).isEqualTo("PREVIOUS_WEEKLY");
            assertThat(weekly.reviewCountChangeRate()).isEqualTo(0.0);
            assertThat(weekly.averageAccuracyChangeRate()).isEqualTo(0.0);
            assertThat(weekly.consecutiveLearningDaysChangeRate()).isEqualTo(0.0);
            assertThat(weekly.averageStudyTimeChangeRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("누적 리포트의 시작일은 없고 종료일은 기준일이다")
        void totalReportHasNoStartDate() {
            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.total().periodLabel()).isEqualTo("TOTAL");
            assertThat(report.total().startDate()).isNull();
            assertThat(report.total().endDate()).isEqualTo(BASE_DATE);
        }
    }

    @Nested
    @DisplayName("주간·월간·누적 집계")
    class Aggregation {

        /**
         * 아래 시나리오의 기대값.
         * <pre>
         * 복습(problem_solve)
         *   2026-01-10 CORRECT 120s (삼각함수)   ← 지난 달 구간
         *   2026-01-11 WRONG   120s (대수)        ← 지난 달 구간
         *   2026-02-01 CORRECT 120s (삼각함수)   ← 월간 구간
         *   2026-02-10 WRONG    60s (대수)        ← 월간 + 지난 주 구간
         *   2026-02-16 CORRECT 600s (대수)        ← 주간 구간
         *   2026-02-17 WRONG   300s (대수)        ← 주간 구간
         *   2026-02-18 PARTIAL 120s (기하)        ← 주간 구간
         *   2026-02-20 WRONG   180s (기하)        ← 주간 구간
         * </pre>
         */
        private void givenSolveHistory() {
            Problem algebra = saveAnalyzedNote(userId, "대수", LocalDateTime.of(2026, 2, 16, 8, 0));
            Problem geometry = saveAnalyzedNote(userId, "기하", LocalDateTime.of(2026, 2, 1, 8, 0));
            Problem trigonometry = saveAnalyzedNote(userId, "삼각함수", LocalDateTime.of(2026, 1, 11, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 20, 8, 0));
            saveNoteWrittenAt(userId, LocalDateTime.of(2026, 1, 10, 8, 0));

            saveSolve(userId, trigonometry, LocalDateTime.of(2026, 1, 10, 9, 0), AnswerStatus.CORRECT, 120);
            saveSolve(userId, algebra, LocalDateTime.of(2026, 1, 11, 9, 0), AnswerStatus.WRONG, 120);
            saveSolve(userId, trigonometry, LocalDateTime.of(2026, 2, 1, 9, 0), AnswerStatus.CORRECT, 120);
            saveSolve(userId, algebra, LocalDateTime.of(2026, 2, 10, 9, 0), AnswerStatus.WRONG, 60);
            saveSolve(userId, algebra, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 600);
            saveSolve(userId, algebra, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 300);
            saveSolve(userId, geometry, LocalDateTime.of(2026, 2, 18, 9, 0), AnswerStatus.PARTIAL, 120);
            saveSolve(userId, geometry, LocalDateTime.of(2026, 2, 20, 9, 0), AnswerStatus.WRONG, 180);

            saveNotePractice(user, LocalDateTime.of(2026, 2, 17, 11, 0));
            saveNotePractice(user, LocalDateTime.of(2026, 2, 21, 11, 0));
            saveNotePractice(user, LocalDateTime.of(2026, 2, 3, 11, 0));
            saveNotePractice(user, LocalDateTime.of(2026, 1, 10, 11, 0));
        }

        @Test
        @DisplayName("주간 집계가 기간 안의 기록만으로 정확히 계산된다")
        void weeklyAggregation() {
            givenSolveHistory();

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.periodLabel()).isEqualTo("WEEKLY");
            assertThat(weekly.startDate()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(weekly.endDate()).isEqualTo(BASE_DATE);
            assertThat(weekly.reviewCount()).as("2/16, 2/17, 2/18, 2/20").isEqualTo(4L);
            assertThat(weekly.noteWriteCount()).as("2/16, 2/20 작성").isEqualTo(2L);
            assertThat(weekly.notePracticeCount()).as("2/17, 2/21 복습노트").isEqualTo(2L);
            assertThat(weekly.averageAccuracy())
                    .as("(1 + 0 + 0.5 + 0) / 4 = 0.375")
                    .isEqualTo(37.5);
            assertThat(weekly.averageStudyTimeMinutes())
                    .as("(600+300+120+180)/4 = 300초 = 5분")
                    .isEqualTo(5.0);
            assertThat(weekly.consecutiveLearningDays()).as("2/16~2/18 사흘").isEqualTo(3);
            assertThat(weakAreaMap(weekly.weakAreas()))
                    .as("오답만 집계: 2/17 대수, 2/20 기하")
                    .containsOnly(
                            entry("대수", 1L),
                            entry("기하", 1L));
        }

        @Test
        @DisplayName("주간 추이는 기간의 모든 날짜를 하루 단위 버킷으로 채운다")
        void weeklyTrend() {
            givenSolveHistory();

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(trendLabels(weekly.trend())).containsExactly(
                    "2026-02-15", "2026-02-16", "2026-02-17", "2026-02-18",
                    "2026-02-19", "2026-02-20", "2026-02-21");
            assertThat(trendMap(weekly.trend()))
                    .containsEntry("2026-02-15", 0L)
                    .containsEntry("2026-02-16", 1L)
                    .containsEntry("2026-02-17", 1L)
                    .containsEntry("2026-02-18", 1L)
                    .containsEntry("2026-02-19", 0L)
                    .containsEntry("2026-02-20", 1L)
                    .containsEntry("2026-02-21", 0L);
        }

        @Test
        @DisplayName("월간 집계는 기준일 기준 28일 구간을 대상으로 한다")
        void monthlyAggregation() {
            givenSolveHistory();

            LearningPeriodReport monthly = learningReportService.getLearningReport(userId, BASE_DATE).monthly();

            assertThat(monthly.startDate()).isEqualTo(LocalDate.of(2026, 1, 25));
            assertThat(monthly.endDate()).isEqualTo(BASE_DATE);
            assertThat(monthly.reviewCount()).as("2/1, 2/10 + 주간 4건").isEqualTo(6L);
            assertThat(monthly.noteWriteCount()).as("2/1, 2/16, 2/20 작성").isEqualTo(3L);
            assertThat(monthly.notePracticeCount()).as("2/3, 2/17, 2/21").isEqualTo(3L);
            assertThat(monthly.averageAccuracy())
                    // MySQL 의 AVG(DECIMAL) 은 소수 5자리까지만 남기므로
                    // 2.5/6 = 0.4166666... 이 0.41667 로 반올림되어 41.667 이 된다.
                    .as("(1+0+0.5+0+0+1)/6 = 0.41667")
                    .isCloseTo(41.667, within(0.001));
            assertThat(monthly.averageStudyTimeMinutes())
                    .as("(600+300+120+180+60+120)/6 = 230초")
                    .isCloseTo(230.0 / 60.0, within(0.0001));
            assertThat(monthly.consecutiveLearningDays()).as("2/16~2/18").isEqualTo(3);
            assertThat(weakAreaMap(monthly.weakAreas()))
                    .containsOnly(
                            entry("대수", 2L),
                            entry("기하", 1L));
        }

        @Test
        @DisplayName("월간 추이는 기준일에서 거꾸로 센 4주 버킷으로 묶인다")
        void monthlyTrend() {
            givenSolveHistory();

            LearningPeriodReport monthly = learningReportService.getLearningReport(userId, BASE_DATE).monthly();

            assertThat(trendLabels(monthly.trend()))
                    .containsExactly("지난 4주", "지난 3주", "지난 2주", "지난 1주");
            assertThat(trendMap(monthly.trend()))
                    .containsEntry("지난 4주", 0L)
                    .containsEntry("지난 3주", 1L)
                    .containsEntry("지난 2주", 1L)
                    .containsEntry("지난 1주", 4L);
        }

        @Test
        @DisplayName("누적 집계는 기간 제한 없이 전체 기록을 대상으로 한다")
        void totalAggregation() {
            givenSolveHistory();

            LearningPeriodReport total = learningReportService.getLearningReport(userId, BASE_DATE).total();

            assertThat(total.reviewCount()).isEqualTo(8L);
            assertThat(total.noteWriteCount()).isEqualTo(5L);
            assertThat(total.notePracticeCount()).isEqualTo(4L);
            assertThat(total.averageAccuracy())
                    .as("(1+0+1+0+1+0+0.5+0)/8 = 0.4375")
                    .isCloseTo(43.75, within(0.0001));
            assertThat(total.averageStudyTimeMinutes())
                    .as("(120+120+120+60+600+300+120+180)/8 = 202.5초")
                    .isCloseTo(202.5 / 60.0, within(0.0001));
            assertThat(total.consecutiveLearningDays()).as("2/16~2/18 사흘이 최장").isEqualTo(3);
            assertThat(weakAreaMap(total.weakAreas()))
                    .containsOnly(
                            entry("대수", 3L),
                            entry("기하", 1L));
        }

        @Test
        @DisplayName("누적 추이는 기준월 포함 최근 6개월을 월 단위로 묶는다")
        void totalTrend() {
            givenSolveHistory();

            LearningPeriodReport total = learningReportService.getLearningReport(userId, BASE_DATE).total();

            assertThat(trendLabels(total.trend())).containsExactly(
                    "2025-09", "2025-10", "2025-11", "2025-12", "2026-01", "2026-02");
            assertThat(trendMap(total.trend()))
                    .containsEntry("2026-01", 2L)
                    .containsEntry("2026-02", 6L)
                    .containsEntry("2025-12", 0L);
        }

        @Test
        @DisplayName("이전 기간과의 변화율을 계산한다")
        void comparisons() {
            givenSolveHistory();

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            LearningComparison weekly = report.weeklyComparison();
            assertThat(weekly.reviewCountChangeRate()).as("4건 vs 지난 주 1건").isEqualTo(300.0);
            assertThat(weekly.averageAccuracyChangeRate())
                    .as("지난 주 정답률이 0이면 증가율은 100으로 고정한다")
                    .isEqualTo(100.0);
            assertThat(weekly.consecutiveLearningDaysChangeRate()).as("3일 vs 1일").isEqualTo(200.0);
            assertThat(weekly.averageStudyTimeChangeRate()).as("5.0분 vs 1.0분").isEqualTo(400.0);

            LearningComparison monthly = report.monthlyComparison();
            assertThat(monthly.basePeriod()).isEqualTo("MONTHLY");
            assertThat(monthly.compareTo()).isEqualTo("PREVIOUS_MONTHLY");
            assertThat(monthly.reviewCountChangeRate()).as("6건 vs 지난 달 구간 2건").isEqualTo(200.0);
            assertThat(monthly.averageAccuracyChangeRate())
                    .as("(41.667 - 50) / 50 * 100")
                    .isCloseTo(-16.666, within(0.001));
            assertThat(monthly.consecutiveLearningDaysChangeRate()).as("3일 vs 2일").isEqualTo(50.0);
            assertThat(monthly.averageStudyTimeChangeRate())
                    .as("(230/60 - 2.0) / 2.0 * 100")
                    .isCloseTo(91.666666, within(0.0001));
        }
    }

    @Nested
    @DisplayName("기간 경계")
    class PeriodBoundary {

        @Test
        @DisplayName("주간 구간은 기준일 포함 7일이며 그 바깥의 기록은 세지 않는다")
        void weeklyRangeIsInclusiveSevenDays() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 1, 1, 0, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 14, 23, 59, 59), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 15, 0, 0, 0), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 21, 23, 59, 59), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 22, 0, 0, 0), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.weekly().reviewCount())
                    .as("2/15 00:00 와 2/21 23:59:59 두 건만 주간 구간")
                    .isEqualTo(2L);
            assertThat(report.total().reviewCount()).as("누적은 네 건 모두 포함").isEqualTo(4L);
        }

        @Test
        @DisplayName("구간 마지막 날 23:59:59.999999 기록도 포함한다")
        void includesLastMicrosecondOfPeriod() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 1, 1, 0, 0));
            saveSolve(userId, problem,
                    LocalDateTime.of(2026, 2, 21, 23, 59, 59, 999_999_000), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.weekly().reviewCount()).isEqualTo(1L);
            assertThat(trendMap(report.weekly().trend())).containsEntry("2026-02-21", 1L);
        }

        @Test
        @DisplayName("월간 구간은 기준일 포함 28일이며 그 바깥의 기록은 세지 않는다")
        void monthlyRangeIsInclusiveTwentyEightDays() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 1, 1, 0, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 24, 23, 59, 59), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 25, 0, 0, 0), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.monthly().reviewCount()).as("1/25 한 건만 월간 구간").isEqualTo(1L);
            assertThat(report.monthly().startDate()).isEqualTo(LocalDate.of(2026, 1, 25));
        }

        @Test
        @DisplayName("달 경계를 걸친 기록도 하나의 구간으로 이어서 센다")
        void spansCalendarMonthBoundary() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 1, 1, 0, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 30, 10, 0), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 31, 10, 0), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 1, 10, 0), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.monthly().reviewCount()).isEqualTo(3L);
            assertThat(report.monthly().consecutiveLearningDays())
                    .as("1/30, 1/31, 2/1 은 달이 달라도 연속 사흘")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("월의 첫날과 마지막날 기록을 모두 집계한다")
        void includesFirstAndLastDayOfMonth() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2025, 1, 1, 0, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 1, 0, 0, 0), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 1, 31, 23, 59, 59), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, LocalDate.of(2026, 1, 31));

            assertThat(trendMap(report.total().trend()))
                    .as("누적 월간 추이의 2026-01 버킷에 두 건 모두 들어간다")
                    .containsEntry("2026-01", 2L);
        }

        @Test
        @DisplayName("윤년 2월 29일 기록도 정상적으로 집계한다")
        void handlesLeapDay() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2024, 2, 29, 8, 0));
            saveSolve(userId, problem, LocalDateTime.of(2024, 2, 29, 10, 0), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, LocalDate.of(2024, 2, 29));

            assertThat(report.weekly().reviewCount()).isEqualTo(1L);
            assertThat(report.weekly().noteWriteCount()).isEqualTo(1L);
            assertThat(trendMap(report.weekly().trend())).containsEntry("2024-02-29", 1L);
        }
    }

    @Nested
    @DisplayName("정답률과 학습 시간")
    class AccuracyAndStudyTime {

        @Test
        @DisplayName("정답 1점, 부분정답 0.5점, 오답과 미확인은 0점으로 평균 낸다")
        void accuracyScoring() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 10, 0), AnswerStatus.PARTIAL, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 11, 0), AnswerStatus.WRONG, 60);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 12, 0), AnswerStatus.UNKNOWN, 60);

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.averageAccuracy())
                    .as("(1 + 0.5 + 0 + 0) / 4 = 0.375")
                    .isEqualTo(37.5);
        }

        @Test
        @DisplayName("학습 시간이 비어 있거나 0인 기록은 평균에서 제외한다")
        void studyTimeIgnoresNullAndZero() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 120);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 10, 0), AnswerStatus.CORRECT, 240);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 11, 0), AnswerStatus.CORRECT, null);
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 12, 0), AnswerStatus.CORRECT, 0);

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.reviewCount()).as("복습 건수는 네 건 모두").isEqualTo(4L);
            assertThat(weekly.averageStudyTimeMinutes())
                    .as("(120 + 240) / 2 = 180초 = 3분")
                    .isEqualTo(3.0);
        }

        @Test
        @DisplayName("학습 시간이 모두 비어 있으면 평균은 0이다")
        void studyTimeIsZeroWhenAllExcluded() {
            Problem problem = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, problem, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, null);

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.averageStudyTimeMinutes()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("취약 유형")
    class WeakAreas {

        @Test
        @DisplayName("오답 수가 많은 순으로 최대 3개만 담는다")
        void topThreeByWrongCount() {
            LocalDateTime day = LocalDateTime.of(2026, 2, 16, 9, 0);
            Problem a = saveAnalyzedNote(userId, "대수", day);
            Problem b = saveAnalyzedNote(userId, "기하", day);
            Problem c = saveAnalyzedNote(userId, "확률", day);
            Problem d = saveAnalyzedNote(userId, "미적분", day);

            for (int i = 0; i < 4; i++) {
                saveSolve(userId, a, day.plusMinutes(i), AnswerStatus.WRONG, 60);
            }
            for (int i = 0; i < 3; i++) {
                saveSolve(userId, b, day.plusMinutes(10 + i), AnswerStatus.WRONG, 60);
            }
            for (int i = 0; i < 2; i++) {
                saveSolve(userId, c, day.plusMinutes(20 + i), AnswerStatus.WRONG, 60);
            }
            saveSolve(userId, d, day.plusMinutes(30), AnswerStatus.WRONG, 60);

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.weakAreas()).hasSize(3);
            assertThat(weekly.weakAreas().stream().map(w -> w.topic()).toList())
                    .containsExactly("대수", "기하", "확률");
            assertThat(weekly.weakAreas().get(0).wrongCount()).isEqualTo(4L);
        }

        @Test
        @DisplayName("정답·부분정답 기록과 분석 결과가 없는 문제는 취약 유형에 잡히지 않는다")
        void onlyWrongSolvesOnAnalyzedProblems() {
            LocalDateTime day = LocalDateTime.of(2026, 2, 16, 9, 0);
            Problem analyzed = saveAnalyzedNote(userId, "대수", day);
            Problem notAnalyzed = saveNoteWrittenAt(userId, day);

            saveSolve(userId, analyzed, day.plusMinutes(1), AnswerStatus.CORRECT, 60);
            saveSolve(userId, analyzed, day.plusMinutes(2), AnswerStatus.PARTIAL, 60);
            saveSolve(userId, notAnalyzed, day.plusMinutes(3), AnswerStatus.WRONG, 60);

            LearningPeriodReport weekly = learningReportService.getLearningReport(userId, BASE_DATE).weekly();

            assertThat(weekly.weakAreas()).isEmpty();
        }
    }

    @Nested
    @DisplayName("복습노트 사용 수")
    class NotePracticeCount {

        @Test
        @DisplayName("NOTE_PRACTICE 미션만 센다")
        void countsOnlyNotePracticeMissions() {
            saveNotePractice(user, LocalDateTime.of(2026, 2, 16, 9, 0));
            saveMissionLog(user, MissionType.PROBLEM_PRACTICE, LocalDateTime.of(2026, 2, 16, 9, 0));
            saveMissionLog(user, MissionType.USER_LOGIN, LocalDateTime.of(2026, 2, 16, 9, 0));
            saveMissionLog(user, MissionType.PROBLEM_WRITE, LocalDateTime.of(2026, 2, 16, 9, 0));

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.weekly().notePracticeCount()).isEqualTo(1L);
            assertThat(report.total().notePracticeCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("사용자 격리")
    class Ownership {

        @Test
        @DisplayName("다른 사용자의 기록은 내 리포트에 섞이지 않는다")
        void otherUsersDataIsExcluded() {
            User other = fixtures.createOtherUser();
            Problem otherProblem = saveAnalyzedNote(other.getId(), "확률", LocalDateTime.of(2026, 2, 17, 8, 0));
            saveSolve(other.getId(), otherProblem, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 999);
            saveNotePractice(other, LocalDateTime.of(2026, 2, 18, 11, 0));

            Problem mine = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, mine, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 60);

            LearningReportResponseDto report = learningReportService.getLearningReport(userId, BASE_DATE);

            assertThat(report.weekly().reviewCount()).isEqualTo(1L);
            assertThat(report.weekly().noteWriteCount()).isEqualTo(1L);
            assertThat(report.weekly().notePracticeCount()).isZero();
            assertThat(report.weekly().averageAccuracy()).isEqualTo(100.0);
            assertThat(report.weekly().averageStudyTimeMinutes()).isEqualTo(1.0);
            assertThat(report.weekly().weakAreas()).isEmpty();
            assertThat(report.total().reviewCount()).isEqualTo(1L);
            assertThat(report.total().weakAreas()).isEmpty();
        }

        @Test
        @DisplayName("같은 기준일이라도 사용자마다 다른 리포트를 만든다")
        void reportsAreScopedPerUser() {
            User other = fixtures.createOtherUser();
            Problem mine = saveNoteWrittenAt(userId, LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(userId, mine, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.CORRECT, 60);

            Problem theirs = saveNoteWrittenAt(other.getId(), LocalDateTime.of(2026, 2, 16, 8, 0));
            saveSolve(other.getId(), theirs, LocalDateTime.of(2026, 2, 16, 9, 0), AnswerStatus.WRONG, 60);
            saveSolve(other.getId(), theirs, LocalDateTime.of(2026, 2, 17, 9, 0), AnswerStatus.WRONG, 60);

            assertThat(learningReportService.getLearningReport(userId, BASE_DATE).weekly().reviewCount())
                    .isEqualTo(1L);
            assertThat(learningReportService.getLearningReport(other.getId(), BASE_DATE).weekly().reviewCount())
                    .isEqualTo(2L);
            assertThat(learningReportService.getLearningReport(userId, BASE_DATE).weekly().averageAccuracy())
                    .as("상대의 오답이 내 정답률을 끌어내리면 안 된다")
                    .isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("기준일 기본값")
    class DefaultBaseDate {

        @Test
        @DisplayName("기준일을 주지 않으면 JVM 기본 시간대와 무관하게 Asia/Seoul 기준 어제를 쓴다")
        void defaultsToSeoulYesterday() {
            LocalDate seoulYesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

            TimeZone originalTimeZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneWithDifferentDateThanSeoul()));

                LearningReportResponseDto report = learningReportService.getLearningReport(userId, null);

                assertThat(report.weekly().endDate())
                        .as("서울 기준 어제가 기준일이어야 한다")
                        .isEqualTo(seoulYesterday);
                assertThat(report.weekly().startDate()).isEqualTo(seoulYesterday.minusDays(6));
                assertThat(report.total().endDate()).isEqualTo(seoulYesterday);
            } finally {
                TimeZone.setDefault(originalTimeZone);
            }
        }

        /** UTC+14 와 UTC-12 는 26시간 차이라 둘 중 최소 하나는 서울과 날짜가 다르다. */
        private ZoneId zoneWithDifferentDateThanSeoul() {
            LocalDate seoulToday = LocalDate.now(ZoneId.of("Asia/Seoul"));
            return List.of(ZoneId.of("Etc/GMT-14"), ZoneId.of("Etc/GMT+12")).stream()
                    .filter(zone -> !LocalDate.now(zone).equals(seoulToday))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("서울과 날짜가 다른 시간대를 찾지 못했다"));
        }
    }
}
