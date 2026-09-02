package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.CANCELED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.FAILED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SCHEDULED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENDING;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENT;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SKIPPED_BY_COMPLETION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 망각곡선 복습 알림 예약/발송 서비스 테스트.
 *
 * <p>FCM 은 실사용자 푸시 경로이므로 베이스에서 목으로 잡혀 있고, 여기서는 "언제 부르고 언제
 * 부르지 않는가"만 검증한다. 상태 전이는 DB 에 실제로 반영된 값으로 확인한다.
 */
@DisplayName("ProblemReviewReminderService")
class ProblemReviewReminderServiceTest extends ProblemTestSupport {

    @Autowired
    private ProblemReviewReminderService service;

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    private User user;
    private Long userId;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
        userId = user.getId();
    }

    private List<ProblemReviewReminder> remindersOf(Long problemId) {
        return reminderRepository.findAll().stream()
                .filter(reminder -> reminder.getProblemId().equals(problemId))
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .toList();
    }

    private ProblemReviewReminder saveDueReminder(Long ownerId, Long problemId, LocalDateTime scheduledAt) {
        return reminderRepository.save(
                ProblemReviewReminder.create(ownerId, problemId, "메모", "출처", 1, 1, scheduledAt));
    }

    private void giveFcmToken(Long ownerId, String token) {
        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto(token), ownerId));
    }

    // ════════════════════════════ 예약 ════════════════════════════

    @Nested
    @DisplayName("신규 문제 알림 예약")
    class ScheduleForNewProblems {

        @Test
        @DisplayName("문제 1개당 망각곡선 간격(1·3·7·14·30일) 5건이 sequence 1~5로 예약된다")
        void createsFiveRowsPerProblem() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 22, 0);

            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(1001L, "메모", "출처", createdAt)));

            List<ProblemReviewReminder> rows = remindersOf(1001L);
            assertThat(rows).hasSize(5);
            assertThat(rows).extracting(ProblemReviewReminder::getSequence).containsExactly(1, 2, 3, 4, 5);
            assertThat(rows).extracting(ProblemReviewReminder::getIntervalDays).containsExactly(1, 3, 7, 14, 30);
            assertThat(rows).allMatch(reminder -> reminder.getStatus() == SCHEDULED);
            assertThat(rows).extracting(ProblemReviewReminder::getScheduledAt)
                    .containsExactly(
                            createdAt.plusDays(1), createdAt.plusDays(3), createdAt.plusDays(7),
                            createdAt.plusDays(14), createdAt.plusDays(30));
        }

        @Test
        @DisplayName("문제 2개를 한 번에 예약하면 10건이 만들어진다")
        void createsRowsForEveryProblem() {
            LocalDateTime createdAt = LocalDateTime.now();

            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(2001L, "메모1", "출처1", createdAt),
                    new ProblemCreatedEvent.ProblemData(2002L, "메모2", "출처2", createdAt)));

            assertThat(reminderRepository.findAll()).hasSize(10);
        }

        @Test
        @DisplayName("같은 문제로 두 번 예약해도 sequence 유니크 제약 때문에 5건으로 유지된다")
        void isIdempotent() {
            ProblemCreatedEvent.ProblemData data =
                    new ProblemCreatedEvent.ProblemData(3001L, "메모", "출처", LocalDateTime.now());

            service.scheduleForNewProblems(userId, List.of(data));
            service.scheduleForNewProblems(userId, List.of(data));

            assertThat(remindersOf(3001L)).hasSize(5);
        }

        @Test
        @DisplayName("일부 sequence 만 남아 있으면 빠진 것만 채워 넣는다")
        void fillsOnlyMissingSequences() {
            LocalDateTime createdAt = LocalDateTime.now();
            reminderRepository.save(
                    ProblemReviewReminder.create(userId, 3101L, "메모", "출처", 1, 1, createdAt.plusDays(1)));

            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(3101L, "메모", "출처", createdAt)));

            assertThat(remindersOf(3101L))
                    .as("이미 있는 sequence 1 은 건드리지 않고 2~5 만 추가한다")
                    .hasSize(5);
        }

        @Test
        @DisplayName("빈 목록으로 호출하면 아무것도 만들지 않는다")
        void createsNothingForEmptyInput() {
            service.scheduleForNewProblems(userId, List.of());

            assertThat(reminderRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("새벽에 작성한 문제의 D+1 알림은 06:00로 미뤄진다")
        void movesNightScheduleToMorning() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 0, 5);

            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(3201L, "메모", "출처", createdAt)));

            assertThat(remindersOf(3201L).get(0).getScheduledAt())
                    .as("자는 시간에 푸시를 보내지 않는다")
                    .isEqualTo(LocalDateTime.of(2026, 7, 19, 6, 0));
        }

        @Test
        @DisplayName("1000자 메모도 스냅샷 컬럼에 잘리지 않고 저장된다")
        void storesLongMemoSnapshot() {
            String memo = "가".repeat(1000);

            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(3301L, memo, "출처", LocalDateTime.now())));

            assertThat(remindersOf(3301L))
                    .allSatisfy(reminder -> assertThat(reminder.getProblemMemoSnapshot()).hasSize(1000));
        }
    }

    // ════════════════════════════ 취소 / 갱신 ════════════════════════════

    @Nested
    @DisplayName("예약 취소와 스냅샷 갱신")
    class CancelAndRefresh {

        @Test
        @DisplayName("문제 삭제 시 대기 중인 알림이 모두 CANCELED 가 된다")
        void cancelsPendingByProblem() {
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(4001L, "메모", "출처", LocalDateTime.now())));

            service.cancelPendingByProblem(4001L);

            assertThat(remindersOf(4001L)).allMatch(reminder -> reminder.getStatus() == CANCELED);
        }

        @Test
        @DisplayName("다른 문제의 예약은 취소되지 않는다")
        void cancelIsScopedToProblem() {
            LocalDateTime now = LocalDateTime.now();
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(4101L, "메모", "출처", now),
                    new ProblemCreatedEvent.ProblemData(4102L, "메모", "출처", now)));

            service.cancelPendingByProblem(4101L);

            assertThat(remindersOf(4102L)).allMatch(reminder -> reminder.getStatus() == SCHEDULED);
        }

        @Test
        @DisplayName("탈퇴 시 사용자의 대기 알림만 취소되고 다른 사용자 것은 남는다")
        void cancelAllByUserIsUserScoped() {
            User other = fixtures.createOtherUser();
            LocalDateTime now = LocalDateTime.now();
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(4201L, "메모", "출처", now)));
            service.scheduleForNewProblems(other.getId(), List.of(
                    new ProblemCreatedEvent.ProblemData(4202L, "메모", "출처", now)));

            service.cancelAllByUser(userId);

            assertThat(remindersOf(4201L)).allMatch(reminder -> reminder.getStatus() == CANCELED);
            assertThat(remindersOf(4202L))
                    .as("사용자별 격리")
                    .allMatch(reminder -> reminder.getStatus() == SCHEDULED);
        }

        @Test
        @DisplayName("메모 수정 시 SCHEDULED 알림의 스냅샷이 갱신된다")
        void refreshesSnapshot() {
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(5001L, "이전 메모", "이전 출처", LocalDateTime.now())));

            service.refreshSnapshot(5001L, "새 메모", "새 출처");

            assertThat(remindersOf(5001L)).allSatisfy(reminder -> {
                assertThat(reminder.getProblemMemoSnapshot()).isEqualTo("새 메모");
                assertThat(reminder.getProblemReferenceSnapshot()).isEqualTo("새 출처");
            });
        }

        @Test
        @DisplayName("취소된 알림의 스냅샷은 갱신하지 않는다")
        void doesNotRefreshCanceledRows() {
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(5101L, "이전 메모", "이전 출처", LocalDateTime.now())));
            service.cancelPendingByProblem(5101L);

            service.refreshSnapshot(5101L, "새 메모", "새 출처");

            assertThat(remindersOf(5101L))
                    .allSatisfy(reminder ->
                            assertThat(reminder.getProblemMemoSnapshot()).isEqualTo("이전 메모"));
        }

        @Test
        @DisplayName("1000자 메모로 갱신해도 저장이 깨지지 않는다")
        void refreshesWithLongMemo() {
            String memo = "나".repeat(1000);
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(5201L, "짧은 메모", "출처", LocalDateTime.now())));

            service.refreshSnapshot(5201L, memo, "출처");

            assertThat(remindersOf(5201L))
                    .allSatisfy(reminder -> assertThat(reminder.getProblemMemoSnapshot()).hasSize(1000));
        }
    }

    @Nested
    @DisplayName("복습 완료로 인한 skip")
    class SkipOnPractice {

        @Test
        @DisplayName("복습 시각 이전의 예약만 SKIPPED_BY_COMPLETION 이 된다")
        void skipsOnlyDueRows() {
            LocalDateTime base = LocalDateTime.of(2026, 7, 18, 22, 0);
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(6001L, "메모", "출처", base)));

            service.skipDuePendingByProblemSolve(userId, 6001L, base.plusDays(1).plusHours(1));

            Map<Integer, ProblemReviewReminderStatus> statusBySequence = remindersOf(6001L).stream()
                    .collect(Collectors.toMap(
                            ProblemReviewReminder::getSequence, ProblemReviewReminder::getStatus));
            assertThat(statusBySequence.get(1)).isEqualTo(SKIPPED_BY_COMPLETION);
            assertThat(statusBySequence.get(2)).isEqualTo(SCHEDULED);
            assertThat(statusBySequence.get(5)).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("아직 도래하지 않은 예약만 있으면 아무것도 skip 되지 않는다")
        void skipsNothingWhenNothingDue() {
            LocalDateTime base = LocalDateTime.now();
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(6101L, "메모", "출처", base)));

            service.skipDuePendingByProblemSolve(userId, 6101L, base);

            assertThat(remindersOf(6101L)).allMatch(reminder -> reminder.getStatus() == SCHEDULED);
        }

        @Test
        @DisplayName("다른 문제의 예약은 skip 되지 않는다")
        void skipIsScopedToProblem() {
            LocalDateTime base = LocalDateTime.of(2026, 7, 18, 22, 0);
            service.scheduleForNewProblems(userId, List.of(
                    new ProblemCreatedEvent.ProblemData(6201L, "메모", "출처", base),
                    new ProblemCreatedEvent.ProblemData(6202L, "메모", "출처", base)));

            service.skipDuePendingByProblemSolve(userId, 6201L, base.plusDays(31));

            assertThat(remindersOf(6202L)).allMatch(reminder -> reminder.getStatus() == SCHEDULED);
        }
    }

    // ════════════════════════════ 발송 ════════════════════════════

    @Nested
    @DisplayName("due 알림 발송")
    class SendDueReminders {

        @Test
        @DisplayName("발송 조건을 모두 만족하면 FCM 을 한 번 부르고 SENT 로 바꾼다")
        void sendsAndMarksSent() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder due = saveDueReminder(userId, 8001L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, times(1))
                    .sendNotificationToAllUserDevice(eq(userId), any(NotificationRequestDto.class));
            assertThat(reminderRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(SENT);
        }

        @Test
        @DisplayName("예약 시각이 아직 오지 않았으면 발송하지 않는다")
        void doesNotSendFutureReminders() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder future = saveDueReminder(userId, 8101L, LocalDateTime.now().plusHours(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
            assertThat(reminderRepository.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("알림을 끈 사용자에게는 발송하지 않는다")
        void skipsNotificationDisabledUser() {
            user.updateNotificationEnabled(false);
            userRepository.save(user);
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder due = saveDueReminder(userId, 9001L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
            assertThat(reminderRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("FCM 토큰이 없으면 발송하지 않는다")
        void skipsUserWithoutFcmToken() {
            ProblemReviewReminder due = saveDueReminder(userId, 10001L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
            assertThat(reminderRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("오늘 이미 보낸 사용자에게는 하루 두 번 보내지 않는다")
        void sendsAtMostOncePerDay() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder alreadySent =
                    saveDueReminder(userId, 11001L, LocalDateTime.now().minusDays(1));
            LocalDateTime sentAt = LocalDateTime.now();
            inTransaction(() -> reminderRepository.markSent(alreadySent.getId(), sentAt, SENT));

            ProblemReviewReminder due = saveDueReminder(userId, 11002L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
            assertThat(reminderRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("due 가 여러 건이면 가장 오래된 것 하나만 보낸다")
        void sendsOnlyEarliestPerUser() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder earlier =
                    saveDueReminder(userId, 12001L, LocalDateTime.now().minusMinutes(10));
            ProblemReviewReminder later =
                    saveDueReminder(userId, 12002L, LocalDateTime.now().minusMinutes(5));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(userId), any());
            assertThat(reminderRepository.findById(earlier.getId()).orElseThrow().getStatus()).isEqualTo(SENT);
            assertThat(reminderRepository.findById(later.getId()).orElseThrow().getStatus()).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("사용자별로 독립 판단한다 - 한 명이 이미 받았어도 다른 사람은 받는다")
        void isolatesUsers() {
            User other = fixtures.createOtherUser();
            giveFcmToken(userId, "token-u1");
            giveFcmToken(other.getId(), "token-u2");

            ProblemReviewReminder alreadySent =
                    saveDueReminder(userId, 13001L, LocalDateTime.now().minusDays(1));
            LocalDateTime sentAt = LocalDateTime.now();
            inTransaction(() -> reminderRepository.markSent(alreadySent.getId(), sentAt, SENT));

            ProblemReviewReminder othersDue =
                    saveDueReminder(other.getId(), 13002L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(other.getId()), any());
            verify(fcmService, never()).sendNotificationToAllUserDevice(eq(userId), any());
            assertThat(reminderRepository.findById(othersDue.getId()).orElseThrow().getStatus()).isEqualTo(SENT);
        }

        @Test
        @DisplayName("FCM 발송이 실패하면 FAILED 로 기록하고 재시도 횟수를 올린다")
        void marksFailedOnFcmError() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder due = saveDueReminder(userId, 8002L, LocalDateTime.now().minusMinutes(1));
            doThrow(new IllegalStateException("enqueue failed"))
                    .when(fcmService).sendNotificationToAllUserDevice(eq(userId), any(NotificationRequestDto.class));

            service.sendDueReminders(LocalDateTime.now());

            ProblemReviewReminder updated = reminderRepository.findById(due.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(FAILED);
            assertThat(updated.getLastErrorMessage()).isEqualTo("enqueue failed");
            assertThat(updated.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("500자를 넘는 에러 메시지는 컬럼 길이에 맞게 잘려 저장된다")
        void truncatesLongErrorMessage() {
            giveFcmToken(userId, "token-1");
            ProblemReviewReminder due = saveDueReminder(userId, 8003L, LocalDateTime.now().minusMinutes(1));
            doThrow(new IllegalStateException("e".repeat(600)))
                    .when(fcmService).sendNotificationToAllUserDevice(eq(userId), any(NotificationRequestDto.class));

            service.sendDueReminders(LocalDateTime.now());

            assertThat(reminderRepository.findById(due.getId()).orElseThrow().getLastErrorMessage())
                    .as("varchar(500) 을 넘기면 Data truncation 으로 발송 배치 전체가 죽는다")
                    .hasSize(500);
        }

        @Test
        @DisplayName("10분 넘게 SENDING 에 멈춰 있는 행은 FAILED 로 복구된다")
        void recoversStuckSendingRows() {
            ProblemReviewReminder stuck = saveDueReminder(userId, 14001L, LocalDateTime.now().minusMinutes(30));
            inTransaction(() ->
                    reminderRepository.tryUpdateStatus(stuck.getId(), SCHEDULED, SENDING, LocalDateTime.now()));
            LocalDateTime stuckSince = LocalDateTime.now().minusMinutes(11);
            inTransaction(() -> entityManager
                    .createNativeQuery("UPDATE problem_review_reminder SET updated_at = ? WHERE id = ?")
                    .setParameter(1, stuckSince)
                    .setParameter(2, stuck.getId())
                    .executeUpdate());

            service.sendDueReminders(LocalDateTime.now());

            ProblemReviewReminder recovered = reminderRepository.findById(stuck.getId()).orElseThrow();
            assertThat(recovered.getStatus()).isEqualTo(FAILED);
            assertThat(recovered.getLastErrorMessage()).isEqualTo("stuck recovery");
        }

        @Test
        @DisplayName("SENDING 상태로 남아 있어도 10분이 지나지 않았으면 건드리지 않는다")
        void keepsRecentSendingRows() {
            ProblemReviewReminder sending = saveDueReminder(userId, 14002L, LocalDateTime.now().minusMinutes(1));
            inTransaction(() ->
                    reminderRepository.tryUpdateStatus(sending.getId(), SCHEDULED, SENDING, LocalDateTime.now()));

            service.sendDueReminders(LocalDateTime.now());

            assertThat(reminderRepository.findById(sending.getId()).orElseThrow().getStatus())
                    .as("발송 중인 행을 성급히 되돌리면 중복 발송이 난다")
                    .isEqualTo(SENDING);
        }

        @Test
        @DisplayName("보낼 것이 없으면 FCM 을 호출하지 않는다")
        void doesNothingWhenNoDueRow() {
            giveFcmToken(userId, "token-1");

            service.sendDueReminders(LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());
        }
    }
}
