package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복습 간격 계산기 단위 테스트.
 *
 * <p>스프링 없이 도는 순수 계산이라 여기서 전이 규칙 전체를 촘촘히 고정해 둔다.
 * 정답이 쌓이면 간격이 배로 늘고, 한 번 틀리면 간격은 처음으로 돌아간다.
 * 추천에서 빠지는 건 연속 여부와 상관없이 정답 기록이 3개 쌓였을 때다.
 */
@DisplayName("ReviewIntervalCalculator")
class ReviewIntervalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Nested
    @DisplayName("정답")
    class Correct {

        @Test
        @DisplayName("첫 정답이면 간격이 1일에서 2일로 늘고 연속 정답 수가 1이 된다")
        void doublesIntervalOnFirstCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0, 0);

            assertThat(schedule.reviewInterval()).isEqualTo(2);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(1);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(2));
            assertThat(schedule.isMastered()).isFalse();
        }

        @Test
        @DisplayName("두 번째 정답이면 간격이 2일에서 4일로 늘어난다")
        void doublesAgainOnSecondCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 2, 1, 1);

            assertThat(schedule.reviewInterval()).isEqualTo(4);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(2);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(4));
        }

        @Test
        @DisplayName("연속 정답 3회면 마스터로 보고 다음 복습을 잡지 않는다")
        void masteredAtThirdConsecutiveCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 4, 2, 2);

            assertThat(schedule.isMastered()).isTrue();
            assertThat(schedule.nextReviewAt()).isNull();
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(3);
            assertThat(schedule.reviewInterval())
                    .as("마스터 시점에는 간격을 더 늘리지 않는다")
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("연속이 아니어도 세 번째 정답이면 마스터로 보고 다음 복습을 잡지 않는다")
        void masteredAtThirdCorrectEvenIfNotConsecutive() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0, 2);

            assertThat(schedule.isMastered()).isTrue();
            assertThat(schedule.nextReviewAt()).isNull();
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("정답이 두 번째면 연속이 아니어도 아직 마스터가 아니다")
        void notMasteredAtSecondCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0, 1);

            assertThat(schedule.isMastered()).isFalse();
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(2));
        }

        @Test
        @DisplayName("간격은 30일을 넘지 않는다")
        void capsIntervalAtThirtyDays() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 20, 0, 0);

            assertThat(schedule.reviewInterval()).isEqualTo(30);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(30));
        }

        @Test
        @DisplayName("이미 30일이어도 30일로 유지된다")
        void keepsMaxInterval() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 30, 1, 1);

            assertThat(schedule.reviewInterval()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("오답 / 부분 정답")
    class WrongOrPartial {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AnswerStatus.class, names = {"WRONG", "PARTIAL"})
        @DisplayName("틀리거나 부분 정답이면 간격과 연속 정답 수가 초기화된다")
        void resetsScheduleOnFailure(AnswerStatus status) {
            var schedule = ReviewIntervalCalculator.calculate(status, 16, 2, 2);

            assertThat(schedule.reviewInterval())
                    .as("%s 이면 처음부터 다시 시작한다", status)
                    .isEqualTo(1);
            assertThat(schedule.consecutiveCorrectCount()).isZero();
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(1));
            assertThat(schedule.isMastered()).isFalse();
        }
    }

    @Nested
    @DisplayName("판정 불가")
    class Unknown {

        @Test
        @DisplayName("UNKNOWN 이면 3일 뒤로 미루되 간격과 연속 정답 수는 그대로 둔다")
        void postponesWithoutChangingProgress() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.UNKNOWN, 8, 2, 2);

            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(3));
            assertThat(schedule.reviewInterval())
                    .as("판정할 수 없는 기록으로 진행도를 바꾸면 안 된다")
                    .isEqualTo(8);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("정답/오답 반복 시나리오")
    class Sequences {

        @Test
        @DisplayName("정답 2회 후 오답이면 다시 1일 간격, 연속 정답 0으로 돌아간다")
        void correctCorrectThenWrong() {
            var first = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0, 0);
            var second = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, first.reviewInterval(), first.consecutiveCorrectCount(), 1);
            var third = ReviewIntervalCalculator.calculate(
                    AnswerStatus.WRONG, second.reviewInterval(), second.consecutiveCorrectCount(), 2);

            assertThat(second.reviewInterval()).isEqualTo(4);
            assertThat(third.reviewInterval()).isEqualTo(1);
            assertThat(third.consecutiveCorrectCount()).isZero();
        }

        @Test
        @DisplayName("정답·오답·정답·오답·정답이면 세 번째 정답에서 마스터가 된다")
        void masteredAfterThreeCorrectWithWrongInBetween() {
            var c1 = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0, 0);
            var w1 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.WRONG, c1.reviewInterval(), c1.consecutiveCorrectCount(), 1);
            var c2 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, w1.reviewInterval(), w1.consecutiveCorrectCount(), 1);
            var w2 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.WRONG, c2.reviewInterval(), c2.consecutiveCorrectCount(), 2);
            var c3 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, w2.reviewInterval(), w2.consecutiveCorrectCount(), 2);

            assertThat(c2.isMastered()).isFalse();
            assertThat(w2.nextReviewAt())
                    .as("틀리면 정답이 두 번 쌓여 있어도 내일 다시 본다")
                    .isEqualTo(TODAY.plusDays(1));
            assertThat(c3.consecutiveCorrectCount()).isEqualTo(1);
            assertThat(c3.isMastered()).isTrue();
        }

        @Test
        @DisplayName("정답만 계속하면 간격이 1→2→4 로 두 배씩 늘다가 3회째에 마스터된다")
        void intervalsDoubleUntilMastery() {
            int interval = 1;
            int consecutive = 0;
            int[] expectedIntervals = {2, 4};

            for (int expected : expectedIntervals) {
                var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, interval, consecutive, consecutive);
                assertThat(schedule.reviewInterval()).isEqualTo(expected);
                interval = schedule.reviewInterval();
                consecutive = schedule.consecutiveCorrectCount();
            }

            assertThat(ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, interval, consecutive, consecutive).isMastered())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("기록 전체로 다시 계산")
    class Replay {

        private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

        private ReviewIntervalCalculator.SolveMark mark(int dayOffset, AnswerStatus status) {
            return new ReviewIntervalCalculator.SolveMark(DAY.plusDays(dayOffset), status);
        }

        @Test
        @DisplayName("기록이 없으면 등록한 날이 복습일이고 간격은 1일이다")
        void startsFromFirstReviewDate() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of());

            assertThat(schedule.nextReviewAt()).isEqualTo(DAY);
            assertThat(schedule.reviewInterval()).isEqualTo(1);
            assertThat(schedule.consecutiveCorrectCount()).isZero();
        }

        @Test
        @DisplayName("다음 복습일은 마지막 기록을 남긴 날부터 센다")
        void countsFromPracticedDate() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(
                    mark(0, AnswerStatus.CORRECT), mark(5, AnswerStatus.CORRECT)));

            assertThat(schedule.reviewInterval()).isEqualTo(4);
            assertThat(schedule.nextReviewAt()).isEqualTo(DAY.plusDays(5 + 4));
        }

        @Test
        @DisplayName("같은 날 다시 맞힌 기록은 건너뛰어 하루에 한 번만 센다")
        void skipsRepeatedCorrectOnSameDay() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(
                    mark(0, AnswerStatus.CORRECT), mark(0, AnswerStatus.CORRECT), mark(0, AnswerStatus.CORRECT)));

            assertThat(schedule.isMastered()).isFalse();
            assertThat(schedule.reviewInterval()).isEqualTo(2);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날이라도 틀린 기록은 반영해 다음 날로 되돌린다")
        void appliesWrongEvenOnSameDay() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(
                    mark(0, AnswerStatus.CORRECT), mark(0, AnswerStatus.WRONG)));

            assertThat(schedule.nextReviewAt()).isEqualTo(DAY.plusDays(1));
            assertThat(schedule.reviewInterval()).isEqualTo(1);
        }

        @Test
        @DisplayName("정답을 남긴 날이 서로 다른 3일이 되면 마스터다")
        void masteredAfterThreeCorrectDays() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(
                    mark(0, AnswerStatus.CORRECT), mark(0, AnswerStatus.CORRECT),
                    mark(2, AnswerStatus.CORRECT), mark(6, AnswerStatus.CORRECT)));

            assertThat(schedule.isMastered()).isTrue();
        }

        @Test
        @DisplayName("마스터한 뒤에 틀리면 다음 날 다시 보고, 그 뒤로 정답 3일을 다시 채워야 마스터다")
        void relearnsAfterWrongFollowingMastery() {
            var marks = new java.util.ArrayList<>(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT),
                    mark(6, AnswerStatus.CORRECT), mark(8, AnswerStatus.WRONG)));

            var afterWrong = ReviewIntervalCalculator.replay(DAY, marks);
            assertThat(afterWrong.isMastered()).isFalse();
            assertThat(afterWrong.nextReviewAt()).isEqualTo(DAY.plusDays(9));

            marks.add(mark(9, AnswerStatus.CORRECT));
            marks.add(mark(11, AnswerStatus.CORRECT));
            assertThat(ReviewIntervalCalculator.replay(DAY, marks).isMastered())
                    .as("틀린 뒤 정답이 2일뿐이라 아직 아니다")
                    .isFalse();

            marks.add(mark(15, AnswerStatus.CORRECT));
            assertThat(ReviewIntervalCalculator.replay(DAY, marks).isMastered()).isTrue();
        }

        @Test
        @DisplayName("중간에 틀리면 그 앞의 정답 날은 세지 않는다")
        void wrongResetsCorrectDays() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT),
                    mark(4, AnswerStatus.PARTIAL), mark(5, AnswerStatus.CORRECT)));

            assertThat(schedule.isMastered()).isFalse();
        }

        @Test
        @DisplayName("판정 불가 기록은 3일 뒤로 미루기만 한다")
        void unknownOnlyPostpones() {
            var schedule = ReviewIntervalCalculator.replay(DAY, List.of(mark(0, AnswerStatus.UNKNOWN)));

            assertThat(schedule.nextReviewAt()).isEqualTo(DAY.plusDays(3));
            assertThat(schedule.reviewInterval()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("정답 날 수와 졸업한 날")
    class MasteryProgress {

        private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

        private ReviewIntervalCalculator.SolveMark mark(int dayOffset, AnswerStatus status) {
            return new ReviewIntervalCalculator.SolveMark(DAY.plusDays(dayOffset), status);
        }

        @Test
        @DisplayName("같은 날 두 번 맞힌 것은 하루로 센다")
        void countsSameDayOnce() {
            var progress = ReviewIntervalCalculator.masteryProgress(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(0, AnswerStatus.CORRECT), mark(1, AnswerStatus.CORRECT)));

            assertThat(progress.correctDayCount()).isEqualTo(2);
            assertThat(progress.isMastered()).isFalse();
            assertThat(progress.masteredOn()).isNull();
        }

        @Test
        @DisplayName("정답 날이 3일째가 된 날이 졸업한 날이고, 그 뒤 정답은 날짜를 바꾸지 않는다")
        void masteredOnThirdCorrectDay() {
            var progress = ReviewIntervalCalculator.masteryProgress(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT), mark(5, AnswerStatus.CORRECT),
                    mark(9, AnswerStatus.CORRECT)));

            assertThat(progress.correctDayCount()).isEqualTo(4);
            assertThat(progress.isMastered()).isTrue();
            assertThat(progress.masteredOn()).isEqualTo(DAY.plusDays(5));
        }

        @Test
        @DisplayName("졸업한 뒤 틀리거나 부분 정답이면 처음부터 다시 센다")
        void wrongOrPartialAfterMasteryResets() {
            var wrong = ReviewIntervalCalculator.masteryProgress(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT), mark(5, AnswerStatus.CORRECT),
                    mark(6, AnswerStatus.WRONG), mark(7, AnswerStatus.CORRECT)));
            var partial = ReviewIntervalCalculator.masteryProgress(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT), mark(5, AnswerStatus.CORRECT),
                    mark(6, AnswerStatus.PARTIAL)));

            assertThat(wrong.correctDayCount()).isEqualTo(1);
            assertThat(wrong.masteredOn()).isNull();
            assertThat(partial.correctDayCount()).isZero();
            assertThat(partial.masteredOn()).isNull();
        }

        @Test
        @DisplayName("UNKNOWN 은 정답 날도 졸업한 날도 건드리지 않는다")
        void unknownKeepsProgress() {
            var progress = ReviewIntervalCalculator.masteryProgress(List.of(
                    mark(0, AnswerStatus.CORRECT), mark(2, AnswerStatus.CORRECT), mark(5, AnswerStatus.CORRECT),
                    mark(6, AnswerStatus.UNKNOWN)));

            assertThat(progress.isMastered()).isTrue();
            assertThat(progress.masteredOn()).isEqualTo(DAY.plusDays(5));
        }

        /**
         * 졸업 규칙이 replay 와 따로 놀면 문제 상세의 정답 n/3 과 보고서가 어긋난다.
         * 하루에 두 기록씩, 세 가지 결과로 만들 수 있는 길이 6의 기록 729 가지를 모두 비교한다.
         *
         * <p>UNKNOWN 은 뺀다. replay 는 졸업 뒤 UNKNOWN 이 오면 3일 뒤 복습일을 잡아 isMastered 가 false 가
         * 되지만, 추천 쿼리는 마지막 오답 이후 정답 날만 세서 그대로 졸업으로 본다. 보고서는 추천 쿼리 쪽을 따른다.
         */
        @Test
        @DisplayName("UNKNOWN 이 없는 모든 짧은 기록에서 replay 의 졸업 판정과 같다")
        void agreesWithReplay() {
            AnswerStatus[] statuses = {AnswerStatus.CORRECT, AnswerStatus.WRONG, AnswerStatus.PARTIAL};
            int length = 6;
            int combinations = (int) Math.pow(statuses.length, length);
            for (int code = 0; code < combinations; code++) {
                List<ReviewIntervalCalculator.SolveMark> marks = new java.util.ArrayList<>();
                int rest = code;
                for (int i = 0; i < length; i++) {
                    marks.add(mark(i / 2, statuses[rest % statuses.length]));
                    rest /= statuses.length;
                }

                assertThat(ReviewIntervalCalculator.masteryProgress(marks).isMastered())
                        .as("기록 %s", marks)
                        .isEqualTo(ReviewIntervalCalculator.replay(DAY, marks).isMastered());
            }
        }
    }
}
