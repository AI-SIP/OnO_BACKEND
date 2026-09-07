package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복습 간격 계산기 단위 테스트.
 *
 * <p>스프링 없이 도는 순수 계산이라 여기서 전이 규칙 전체를 촘촘히 고정해 둔다.
 * 정답이 쌓이면 간격이 배로 늘고, 한 번 틀리면 처음으로 돌아간다.
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
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0);

            assertThat(schedule.reviewInterval()).isEqualTo(2);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(1);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(2));
            assertThat(schedule.isMastered()).isFalse();
        }

        @Test
        @DisplayName("두 번째 정답이면 간격이 2일에서 4일로 늘어난다")
        void doublesAgainOnSecondCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 2, 1);

            assertThat(schedule.reviewInterval()).isEqualTo(4);
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(2);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(4));
        }

        @Test
        @DisplayName("연속 정답 3회면 마스터로 보고 다음 복습을 잡지 않는다")
        void masteredAtThirdConsecutiveCorrect() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 4, 2);

            assertThat(schedule.isMastered()).isTrue();
            assertThat(schedule.nextReviewAt()).isNull();
            assertThat(schedule.consecutiveCorrectCount()).isEqualTo(3);
            assertThat(schedule.reviewInterval())
                    .as("마스터 시점에는 간격을 더 늘리지 않는다")
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("간격은 30일을 넘지 않는다")
        void capsIntervalAtThirtyDays() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 20, 0);

            assertThat(schedule.reviewInterval()).isEqualTo(30);
            assertThat(schedule.nextReviewAt()).isEqualTo(TODAY.plusDays(30));
        }

        @Test
        @DisplayName("이미 30일이어도 30일로 유지된다")
        void keepsMaxInterval() {
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 30, 1);

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
            var schedule = ReviewIntervalCalculator.calculate(status, 16, 2);

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
            var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.UNKNOWN, 8, 2);

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
            var first = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, 1, 0);
            var second = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, first.reviewInterval(), first.consecutiveCorrectCount());
            var third = ReviewIntervalCalculator.calculate(
                    AnswerStatus.WRONG, second.reviewInterval(), second.consecutiveCorrectCount());

            assertThat(second.reviewInterval()).isEqualTo(4);
            assertThat(third.reviewInterval()).isEqualTo(1);
            assertThat(third.consecutiveCorrectCount()).isZero();
        }

        @Test
        @DisplayName("오답 후 정답 3회를 다시 채우면 마스터가 된다")
        void recoversToMasteryAfterWrong() {
            var afterWrong = ReviewIntervalCalculator.calculate(AnswerStatus.WRONG, 8, 2);
            var c1 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, afterWrong.reviewInterval(), afterWrong.consecutiveCorrectCount());
            var c2 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, c1.reviewInterval(), c1.consecutiveCorrectCount());
            var c3 = ReviewIntervalCalculator.calculate(
                    AnswerStatus.CORRECT, c2.reviewInterval(), c2.consecutiveCorrectCount());

            assertThat(c1.consecutiveCorrectCount()).isEqualTo(1);
            assertThat(c2.consecutiveCorrectCount()).isEqualTo(2);
            assertThat(c3.isMastered()).isTrue();
        }

        @Test
        @DisplayName("정답만 계속하면 간격이 1→2→4 로 두 배씩 늘다가 3회째에 마스터된다")
        void intervalsDoubleUntilMastery() {
            int interval = 1;
            int consecutive = 0;
            int[] expectedIntervals = {2, 4};

            for (int expected : expectedIntervals) {
                var schedule = ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, interval, consecutive);
                assertThat(schedule.reviewInterval()).isEqualTo(expected);
                interval = schedule.reviewInterval();
                consecutive = schedule.consecutiveCorrectCount();
            }

            assertThat(ReviewIntervalCalculator.calculate(AnswerStatus.CORRECT, interval, consecutive).isMastered())
                    .isTrue();
        }
    }
}
