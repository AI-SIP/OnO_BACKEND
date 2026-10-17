package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.CANCELED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.EXPIRED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.FAILED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SCHEDULED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENDING;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * 복습 알림 저장소 쿼리를 실제 MySQL 에서 검증한다.
 *
 * <p>{@code countTodayPushesByUser} 는 {@code COUNT(DISTINCT CASE ...)} 를 인터페이스 프로젝션으로 받는다.
 * JPQL 이 Hibernate 에서 그대로 해석되는지, 묶어 보낸 행을 푸시 한 건으로 세는지가 하루 상한의 근거라
 * 목이 아니라 컨테이너 DB 위에서 돌린다. 기준일은 고정 날짜를 넘겨 실행 시각과 무관하게 한다.
 */
@DisplayName("ProblemReviewReminderRepository")
class ProblemReviewReminderRepositoryTest extends ProblemTestSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 18);
    private static final LocalDateTime START_OF_DAY = DAY.atStartOfDay();
    private static final LocalDateTime END_OF_DAY = DAY.plusDays(1).atStartOfDay();

    private final AtomicLong problemIdSequence = new AtomicLong(100_000L);

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUpUsers() {
        userId = fixtures.createUser().getId();
        otherUserId = fixtures.createOtherUser().getId();
    }

    // ─────────────────────────── 픽스처 ───────────────────────────

    private ProblemReviewReminder scheduled(Long ownerId, LocalDateTime scheduledAt) {
        return reminderRepository.saveAndFlush(ProblemReviewReminder.create(
                ownerId, problemIdSequence.incrementAndGet(), "메모", "출처", 1, 1, scheduledAt));
    }

    /** 실제 발송 경로처럼 선점(updatedAt = at) 뒤 같은 시각으로 SENT 기록한다. */
    private ProblemReviewReminder sent(Long ownerId, LocalDateTime at) {
        ProblemReviewReminder reminder = scheduled(ownerId, at.minusHours(1));
        inTransaction(() -> {
            reminderRepository.tryUpdateStatus(reminder.getId(), SCHEDULED, SENDING, at);
            reminderRepository.markSent(reminder.getId(), at, SENT);
        });
        return reminder;
    }

    private ProblemReviewReminder sending(Long ownerId, LocalDateTime at) {
        ProblemReviewReminder reminder = scheduled(ownerId, at.minusHours(1));
        inTransaction(() -> reminderRepository.tryUpdateStatus(reminder.getId(), SCHEDULED, SENDING, at));
        return reminder;
    }

    private ProblemReviewReminder failed(Long ownerId, LocalDateTime at) {
        ProblemReviewReminder reminder = sending(ownerId, at);
        inTransaction(() -> reminderRepository.markFailed(reminder.getId(), "enqueue failed", FAILED));
        return reminder;
    }

    private void giveFcmToken(Long ownerId) {
        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("token-" + ownerId), ownerId));
    }

    private Map<Long, Long> countToday(Collection<Long> userIds) {
        return reminderRepository.countTodayPushesByUser(userIds, SENT, SENDING, START_OF_DAY, END_OF_DAY)
                .stream()
                .collect(Collectors.toMap(ReminderPushCount::getUserId, ReminderPushCount::getPushCount));
    }

    // ─────────────────────────── 오늘 푸시 수 ───────────────────────────

    @Nested
    @DisplayName("countTodayPushesByUser")
    class CountTodayPushes {

        @Test
        @DisplayName("같은 sentAt 을 공유하는 SENT 3행은 푸시 1건으로 센다")
        void countsBundleAsOnePush() {
            LocalDateTime poll = DAY.atTime(10, 0);
            sent(userId, poll);
            sent(userId, poll);
            sent(userId, poll);

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("다른 시각에 SENT 된 2건은 2건으로 센다")
        void countsDifferentPollsSeparately() {
            sent(userId, DAY.atTime(10, 0));
            sent(userId, DAY.atTime(10, 5));

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 2L));
        }

        @Test
        @DisplayName("묶음과 단건이 섞여 있으면 폴링 시각의 가짓수로 센다")
        void countsMixedBundlesByDistinctPoll() {
            LocalDateTime firstPoll = DAY.atTime(9, 0);
            sent(userId, firstPoll);
            sent(userId, firstPoll);
            sent(userId, DAY.atTime(11, 0));
            LocalDateTime thirdPoll = DAY.atTime(13, 0);
            sent(userId, thirdPoll);
            sent(userId, thirdPoll);
            sent(userId, thirdPoll);

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 3L));
        }

        @Test
        @DisplayName("선점만 된 SENDING 행도 이미 나가는 중인 푸시로 센다")
        void countsSendingRows() {
            sent(userId, DAY.atTime(10, 0));
            sending(userId, DAY.atTime(10, 5));

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 2L));
        }

        @Test
        @DisplayName("SENDING 만 있는 사용자도 결과에 나온다")
        void countsUserWithOnlySendingRows() {
            sending(userId, DAY.atTime(10, 5));

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("같은 폴링에서 일부만 SENT 로 기록되고 나머지가 SENDING 이어도 1건으로 센다")
        void countsHalfRecordedBundleAsOnePush() {
            LocalDateTime poll = DAY.atTime(10, 0);
            sent(userId, poll);
            sending(userId, poll);
            sending(userId, poll);

            assertThat(countToday(List.of(userId)))
                    .as("SENT 는 sentAt, SENDING 은 updatedAt 을 보지만 둘 다 같은 폴링 시각이다")
                    .containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("어제 보낸 것과 어제 선점된 것은 세지 않는다")
        void excludesYesterday() {
            sent(userId, START_OF_DAY.minusNanos(1_000));
            sent(userId, DAY.minusDays(1).atTime(12, 0));
            sending(userId, DAY.minusDays(1).atTime(20, 59));
            sent(userId, DAY.atTime(10, 0));

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("구간은 자정 포함, 다음 날 자정 미포함이다")
        void dayBoundaryIsHalfOpen() {
            sent(userId, START_OF_DAY);
            sent(userId, END_OF_DAY);
            sending(userId, END_OF_DAY);

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("FAILED, CANCELED, EXPIRED, SCHEDULED 행은 세지 않는다")
        void excludesNonPushStatuses() {
            failed(userId, DAY.atTime(10, 0));
            ProblemReviewReminder canceled = scheduled(userId, DAY.atTime(9, 0));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(canceled.getId()), CANCELED));
            ProblemReviewReminder expired = scheduled(userId, DAY.minusDays(5).atTime(9, 0));
            inTransaction(() -> reminderRepository.expireOverdueRows(
                    SCHEDULED, EXPIRED, DAY.minusDays(3).atStartOfDay(), DAY.atTime(10, 30)));
            scheduled(userId, DAY.atTime(9, 30));

            assertThat(reminderRepository.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(EXPIRED);
            assertThat(countToday(List.of(userId)))
                    .as("보내지 못했거나 보내지 않은 행은 푸시가 아니다")
                    .isEmpty();
        }

        @Test
        @DisplayName("소프트 딜리트된 행은 세지 않는다")
        void excludesSoftDeletedRows() {
            ProblemReviewReminder deleted = sent(userId, DAY.atTime(10, 0));
            sent(userId, DAY.atTime(11, 0));
            inTransaction(() -> entityManager
                    .createNativeQuery("UPDATE problem_review_reminder SET deleted_at = ? WHERE id = ?")
                    .setParameter(1, DAY.atTime(12, 0))
                    .setParameter(2, deleted.getId())
                    .executeUpdate());

            assertThat(countToday(List.of(userId))).containsOnly(entry(userId, 1L));
        }

        @Test
        @DisplayName("요청한 사용자만 세고 다른 사용자의 행은 섞이지 않는다")
        void isScopedToRequestedUsers() {
            LocalDateTime poll = DAY.atTime(10, 0);
            sent(userId, poll);
            sent(otherUserId, poll);
            sent(otherUserId, DAY.atTime(11, 0));
            sending(otherUserId, DAY.atTime(12, 0));

            assertThat(countToday(List.of(userId)))
                    .as("같은 폴링 시각을 공유해도 다른 사용자의 행은 내 수에 들어오지 않는다")
                    .containsOnly(entry(userId, 1L));
            assertThat(countToday(List.of(userId, otherUserId)))
                    .containsOnly(entry(userId, 1L), entry(otherUserId, 3L));
        }

        @Test
        @DisplayName("오늘 푸시가 없는 사용자는 결과에 나오지 않는다")
        void omitsUsersWithoutPushes() {
            sent(otherUserId, DAY.atTime(10, 0));

            assertThat(countToday(List.of(userId, otherUserId))).containsOnly(entry(otherUserId, 1L));
        }
    }

    // ─────────────────────────── due 조회 ───────────────────────────

    @Nested
    @DisplayName("findDueReminders")
    class FindDueReminders {

        private final LocalDateTime now = DAY.atTime(12, 0);

        private List<ProblemReviewReminder> findDue() {
            return reminderRepository.findDueReminders(SCHEDULED, now, PageRequest.of(0, 500));
        }

        @Test
        @DisplayName("오늘 이미 보냈거나 선점 중인 사용자의 due 도 후보에 나온다")
        void includesUsersAlreadyHandledToday() {
            giveFcmToken(userId);
            sent(userId, DAY.atTime(10, 0));
            sending(userId, DAY.atTime(11, 55));
            ProblemReviewReminder due = scheduled(userId, now.minusMinutes(1));

            assertThat(findDue())
                    .as("하루 한 건 조건은 없어졌고 상한은 서비스가 푸시 수로 건다")
                    .extracting(ProblemReviewReminder::getId)
                    .containsExactly(due.getId());
        }

        @Test
        @DisplayName("한 사용자의 due 여러 건이 모두 예약 시각 순으로 나온다")
        void returnsEveryDueRowOfUserInScheduleOrder() {
            giveFcmToken(userId);
            ProblemReviewReminder later = scheduled(userId, now.minusMinutes(5));
            ProblemReviewReminder earlier = scheduled(userId, now.minusMinutes(30));
            ProblemReviewReminder exactlyNow = scheduled(userId, now);
            scheduled(userId, now.plusSeconds(1));

            assertThat(findDue())
                    .extracting(ProblemReviewReminder::getId)
                    .containsExactly(earlier.getId(), later.getId(), exactlyNow.getId());
        }

        @Test
        @DisplayName("알림을 껐거나 토큰이 없는 사용자의 due 는 나오지 않는다")
        void excludesUnreachableUsers() {
            giveFcmToken(userId);
            User disabled = userRepository.findById(userId).orElseThrow();
            disabled.updateNotificationEnabled(false);
            userRepository.save(disabled);
            scheduled(userId, now.minusMinutes(1));
            scheduled(otherUserId, now.minusMinutes(1));

            assertThat(findDue()).isEmpty();
        }

        @Test
        @DisplayName("SCHEDULED 가 아닌 행은 나오지 않는다")
        void excludesNonScheduledRows() {
            giveFcmToken(userId);
            sent(userId, now.minusMinutes(10));
            sending(userId, now.minusMinutes(5));
            failed(userId, now.minusMinutes(3));

            assertThat(findDue()).isEmpty();
        }
    }
}
