package com.aisip.OnO.backend.studyroom.quartz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 챌린지 알림 발송 시각 검증.
 *
 * <p>앱이 마감을 그날 23:59:59 로 보내기 때문에(OnO_FRONT ChallengeCreateSheet.dart) D-1 알림이
 * 밤 23:59 에 나가던 문제를 막는 계산이다. 실제 발송은 하지 않고 시각 계산만 확인한다.
 */
@DisplayName("챌린지 알림 발송 시각 정책")
class ChallengeNotificationTimePolicyTest {

    private ChallengeNotificationTimePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ChallengeNotificationTimePolicy();
    }

    @Nested
    @DisplayName("D-1 알림")
    class OneDayLeft {

        @Test
        @DisplayName("마감이 23:59:59 면 전날 밤이 아니라 전날 18:00 에 보낸다")
        void endOfDayDeadlineFiresAtEvening() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 10, 10, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 20, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);

            Optional<LocalDateTime> fireAt = policy.resolveOneDayLeftFireAt(startAt, endAt, now);

            assertThat(fireAt).as("D-1 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 19, 18, 0));
        }

        @Test
        @DisplayName("맞춘 시각이 이미 지났으면 다음 기준 시각인 다음 날 09:00 으로 넘긴다")
        void passedAnchorMovesToNextAnchor() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 19, 8, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 20, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 19, 20, 0);

            Optional<LocalDateTime> fireAt = policy.resolveOneDayLeftFireAt(startAt, endAt, now);

            assertThat(fireAt).as("18:00 이 지난 뒤의 D-1 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 20, 9, 0));
        }

        @Test
        @DisplayName("마감이 임박해 다음 기준 시각이 마감을 넘기면 보내지 않는다")
        void nextAnchorAfterDeadlineIsNotSent() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 9, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 19, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 19, 19, 0);

            Optional<LocalDateTime> fireAt = policy.resolveOneDayLeftFireAt(startAt, endAt, now);

            assertThat(fireAt).as("마감 임박 D-1 발송 시각").isEmpty();
        }

        @Test
        @DisplayName("기간이 하루 이하면 D-1 시점이 시작보다 이르므로 보내지 않는다")
        void challengeShorterThanOneDaySendsNothing() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 10, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 18, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 0);

            Optional<LocalDateTime> fireAt = policy.resolveOneDayLeftFireAt(startAt, endAt, now);

            assertThat(fireAt).as("하루짜리 챌린지의 D-1 발송 시각").isEmpty();
        }

        @Test
        @DisplayName("발송 시각은 절대 마감 이후로 밀리지 않는다")
        void neverFiresAfterDeadline() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 9, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 20, 23, 59, 59);

            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime now = LocalDateTime.of(2026, 9, 19, hour, 30);
                policy.resolveOneDayLeftFireAt(startAt, endAt, now)
                        .ifPresent(fireAt -> assertThat(fireAt)
                                .as("now=%s 일 때 D-1 발송 시각", now)
                                .isAfter(now)
                                .isBeforeOrEqualTo(endAt));
            }
        }
    }

    @Nested
    @DisplayName("중간 알림")
    class Halfway {

        @Test
        @DisplayName("중간 지점이 새벽이면 같은 날 09:00 으로 맞춘다")
        void dawnHalfwayMovesToMorning() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 22, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 19, 10, 0);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 22, 0);

            Optional<LocalDateTime> fireAt = policy.resolveHalfwayFireAt(startAt, endAt, now);

            assertThat(fireAt).as("새벽 04:00 중간 지점의 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 19, 9, 0));
        }

        @Test
        @DisplayName("중간 지점이 저녁에 가까우면 같은 날 18:00 으로 맞춘다")
        void eveningHalfwayMovesToEvening() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 10, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 18, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 0);

            Optional<LocalDateTime> fireAt = policy.resolveHalfwayFireAt(startAt, endAt, now);

            assertThat(fireAt).as("하루짜리 챌린지의 중간 알림 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 18, 18, 0));
        }

        @Test
        @DisplayName("중간 지점이 09:00 과 18:00 의 정확히 가운데면 이른 09:00 으로 맞춘다")
        void middleOfTwoAnchorsPicksMorning() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 13, 30);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 20, 13, 30);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 13, 30);

            Optional<LocalDateTime> fireAt = policy.resolveHalfwayFireAt(startAt, endAt, now);

            assertThat(fireAt).as("13:30 중간 지점의 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 19, 9, 0));
        }

        @Test
        @DisplayName("맞춘 시각이 이미 지났으면 다음 기준 시각으로 넘긴다")
        void passedHalfwayMovesToNextAnchor() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 14, 9, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 20, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 17, 19, 0);

            Optional<LocalDateTime> fireAt = policy.resolveHalfwayFireAt(startAt, endAt, now);

            assertThat(fireAt).as("중간 지점(9/17 16:29)의 18:00 이 지난 뒤의 발송 시각")
                    .contains(LocalDateTime.of(2026, 9, 18, 9, 0));
        }
    }

    @Nested
    @DisplayName("공통")
    class Common {

        @Test
        @DisplayName("이미 끝난 챌린지는 두 알림 모두 보내지 않는다")
        void finishedChallengeSendsNothing() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 1, 9, 0);
            LocalDateTime endAt = LocalDateTime.of(2026, 9, 10, 23, 59, 59);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 9, 0);

            assertThat(policy.resolveHalfwayFireAt(startAt, endAt, now)).as("중간 알림").isEmpty();
            assertThat(policy.resolveOneDayLeftFireAt(startAt, endAt, now)).as("D-1 알림").isEmpty();
        }

        @Test
        @DisplayName("어떤 마감 시각이 와도 발송 시각은 09:00 아니면 18:00 이다")
        void fireTimeIsAlwaysMorningOrEvening() {
            LocalDateTime startAt = LocalDateTime.of(2026, 9, 18, 0, 0);
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 0, 0);

            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime endAt = LocalDateTime.of(2026, 9, 25, hour, 17, 31);

                policy.resolveHalfwayFireAt(startAt, endAt, now)
                        .ifPresent(fireAt -> assertThatIsAnchor(fireAt, "중간 알림", endAt));
                policy.resolveOneDayLeftFireAt(startAt, endAt, now)
                        .ifPresent(fireAt -> assertThatIsAnchor(fireAt, "D-1 알림", endAt));
            }
        }

        private void assertThatIsAnchor(LocalDateTime fireAt, String label, LocalDateTime endAt) {
            assertThat(fireAt.toLocalTime())
                    .as("%s 발송 시각 (마감 %s)", label, endAt)
                    .isIn(LocalTime.of(9, 0), LocalTime.of(18, 0));
        }
    }
}
