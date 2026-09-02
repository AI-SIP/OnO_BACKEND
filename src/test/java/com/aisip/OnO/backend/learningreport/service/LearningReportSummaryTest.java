package com.aisip.OnO.backend.learningreport.service;

import com.aisip.OnO.backend.learningreport.dto.LearningReportSummaryResponseDto;
import com.aisip.OnO.backend.learningreport.support.LearningReportTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요약은 "이번 달"과 "지난 달"을 달력 기준으로 자른다. 기준 시각이 {@code LocalDate.now(KST)} 이므로
 * 픽스처도 실행 시점의 달을 기준으로 만들어야 실행 날짜와 무관하게 통과한다.
 */
@DisplayName("LearningReportService - 월간 요약")
class LearningReportSummaryTest extends LearningReportTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MONTHLY_REVIEW_GOAL = 30;

    private Long userId;
    private Problem problem;
    private YearMonth thisMonth;
    private YearMonth lastMonth;

    @BeforeEach
    void setUp() {
        userId = fixtures.createUser("report-summary").getId();
        thisMonth = YearMonth.from(LocalDate.now(KST));
        lastMonth = thisMonth.minusMonths(1);
        problem = saveNoteWrittenAt(userId, thisMonth.atDay(1).atStartOfDay());
    }

    private void solvesInThisMonth(int count) {
        solvesIn(thisMonth, count);
    }

    private void solvesInLastMonth(int count) {
        solvesIn(lastMonth, count);
    }

    private void solvesIn(YearMonth month, int count) {
        LocalDateTime start = month.atDay(1).atTime(1, 0);
        for (int i = 0; i < count; i++) {
            saveSolve(userId, problem, start.plusMinutes(i), AnswerStatus.CORRECT, 60);
        }
    }

    @Nested
    @DisplayName("기본 집계")
    class Aggregation {

        @Test
        @DisplayName("기록이 없으면 0 기반 요약을 만든다")
        void emptySummary() {
            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthLabel())
                    .isEqualTo(thisMonth.getYear() + "년 " + thisMonth.getMonthValue() + "월");
            assertThat(summary.monthlyReviewCount()).isZero();
            assertThat(summary.previousMonthlyReviewCount()).isZero();
            assertThat(summary.reviewCountDiff()).isZero();
            assertThat(summary.reviewCountChangeRate())
                    .as("이번 달도 지난 달도 0이면 변화율은 0")
                    .isEqualTo(0.0);
            assertThat(summary.monthlyReviewGoal()).isEqualTo(MONTHLY_REVIEW_GOAL);
            assertThat(summary.monthlyReviewGoalAchieved()).isFalse();
            assertThat(summary.remainingReviewCountToGoal()).isEqualTo(MONTHLY_REVIEW_GOAL);
            assertThat(summary.summaryMessage()).isEqualTo("이번 달 복습 기록을 만들어보세요");
            assertThat(OffsetDateTime.parse(summary.generatedAt()))
                    .as("생성 시각은 KST 오프셋을 가진 파싱 가능한 값이어야 한다")
                    .isNotNull();
        }

        @Test
        @DisplayName("이번 달이 지난 달보다 많으면 증가분과 증가율을 계산한다")
        void increasedFromLastMonth() {
            solvesInThisMonth(3);
            solvesInLastMonth(2);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewCount()).isEqualTo(3L);
            assertThat(summary.previousMonthlyReviewCount()).isEqualTo(2L);
            assertThat(summary.reviewCountDiff()).isEqualTo(1L);
            assertThat(summary.reviewCountChangeRate()).as("(3-2)/2*100").isEqualTo(50.0);
            assertThat(summary.remainingReviewCountToGoal()).isEqualTo(27L);
            assertThat(summary.summaryMessage()).isEqualTo("지난달보다 1문제 더 복습했어요");
        }

        @Test
        @DisplayName("이번 달이 지난 달보다 적으면 감소분과 음수 증가율을 계산한다")
        void decreasedFromLastMonth() {
            solvesInThisMonth(2);
            solvesInLastMonth(5);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.reviewCountDiff()).isEqualTo(-3L);
            assertThat(summary.reviewCountChangeRate()).as("(2-5)/5*100").isEqualTo(-60.0);
            assertThat(summary.summaryMessage()).isEqualTo("지난달보다 3문제 적게 복습했어요");
        }

        @Test
        @DisplayName("이번 달과 지난 달이 같으면 같은 페이스로 안내한다")
        void samePaceAsLastMonth() {
            solvesInThisMonth(4);
            solvesInLastMonth(4);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.reviewCountDiff()).isZero();
            assertThat(summary.reviewCountChangeRate()).isEqualTo(0.0);
            assertThat(summary.summaryMessage()).isEqualTo("지난달과 같은 페이스예요");
        }

        @Test
        @DisplayName("지난 달 기록이 없으면 증가율을 100%로 고정한다")
        void startedThisMonth() {
            solvesInThisMonth(3);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.previousMonthlyReviewCount()).isZero();
            assertThat(summary.reviewCountChangeRate()).isEqualTo(100.0);
            assertThat(summary.summaryMessage()).isEqualTo("이번 달 복습을 시작했어요");
        }

        @Test
        @DisplayName("이번 달 기록이 없고 지난 달만 있으면 -100%가 된다")
        void stoppedThisMonth() {
            solvesInLastMonth(4);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewCount()).isZero();
            assertThat(summary.reviewCountDiff()).isEqualTo(-4L);
            assertThat(summary.reviewCountChangeRate()).isEqualTo(-100.0);
            assertThat(summary.summaryMessage()).isEqualTo("이번 달 복습 기록을 만들어보세요");
        }

        @Test
        @DisplayName("증가율은 소수 첫째 자리까지만 남긴다")
        void changeRateIsRoundedToOneDecimal() {
            solvesInThisMonth(1);
            solvesInLastMonth(3);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.reviewCountChangeRate())
                    .as("(1-3)/3*100 = -66.666... -> -66.7")
                    .isEqualTo(-66.7);
        }
    }

    @Nested
    @DisplayName("월간 목표")
    class MonthlyGoal {

        @Test
        @DisplayName("목표에 도달하면 달성으로 표시하고 남은 개수는 0이 된다")
        void goalAchieved() {
            solvesInThisMonth(MONTHLY_REVIEW_GOAL);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewCount()).isEqualTo(MONTHLY_REVIEW_GOAL);
            assertThat(summary.monthlyReviewGoalAchieved()).isTrue();
            assertThat(summary.remainingReviewCountToGoal()).isZero();
        }

        @Test
        @DisplayName("목표에 한 건 모자라면 아직 달성이 아니다")
        void oneShortOfGoal() {
            solvesInThisMonth(MONTHLY_REVIEW_GOAL - 1);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewGoalAchieved()).isFalse();
            assertThat(summary.remainingReviewCountToGoal()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("달 경계")
    class MonthBoundary {

        @Test
        @DisplayName("이번 달 첫날 자정부터 마지막 순간까지를 이번 달로 센다")
        void includesWholeCurrentMonth() {
            saveSolve(userId, problem, thisMonth.atDay(1).atStartOfDay(), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem,
                    thisMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_000), AnswerStatus.CORRECT, 60);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("지난 달 마지막 순간은 지난 달로, 다음 달 첫 순간은 어느 쪽에도 세지 않는다")
        void excludesAdjacentMonths() {
            saveSolve(userId, problem,
                    lastMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_000), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem,
                    thisMonth.plusMonths(1).atDay(1).atStartOfDay(), AnswerStatus.CORRECT, 60);
            saveSolve(userId, problem,
                    thisMonth.minusMonths(2).atDay(1).atTime(1, 0), AnswerStatus.CORRECT, 60);

            LearningReportSummaryResponseDto summary = learningReportService.getLearningReportSummary(userId);

            assertThat(summary.monthlyReviewCount()).as("다음 달 기록은 이번 달이 아니다").isZero();
            assertThat(summary.previousMonthlyReviewCount())
                    .as("지난 달 마지막 순간 한 건. 두 달 전 기록은 제외")
                    .isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("캐시와 사용자 격리")
    class CacheAndOwnership {

        @Test
        @DisplayName("같은 날 두 번째 조회는 캐시된 요약을 그대로 준다")
        void reusesCachedSummary() {
            solvesInThisMonth(2);

            LearningReportSummaryResponseDto first = learningReportService.getLearningReportSummary(userId);
            solvesInThisMonth(3);
            LearningReportSummaryResponseDto second = learningReportService.getLearningReportSummary(userId);

            assertThat(second.monthlyReviewCount())
                    .as("캐시 적중이라 새로 추가한 복습은 아직 반영되지 않는다")
                    .isEqualTo(first.monthlyReviewCount());
            assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        }

        @Test
        @DisplayName("다른 사용자의 복습은 내 요약에 잡히지 않는다")
        void isolatesUsers() {
            User other = fixtures.createOtherUser();
            Problem otherProblem = saveNoteWrittenAt(other.getId(), thisMonth.atDay(1).atStartOfDay());
            for (int i = 0; i < 5; i++) {
                saveSolve(other.getId(), otherProblem,
                        thisMonth.atDay(1).atTime(2, 0).plusMinutes(i), AnswerStatus.CORRECT, 60);
            }
            solvesInThisMonth(1);

            LearningReportSummaryResponseDto mine = learningReportService.getLearningReportSummary(userId);
            LearningReportSummaryResponseDto theirs =
                    learningReportService.getLearningReportSummary(other.getId());

            assertThat(mine.monthlyReviewCount()).isEqualTo(1L);
            assertThat(theirs.monthlyReviewCount()).isEqualTo(5L);
        }
    }
}
