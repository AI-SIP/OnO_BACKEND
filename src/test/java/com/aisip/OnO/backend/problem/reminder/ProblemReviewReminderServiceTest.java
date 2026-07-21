package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.config.rabbitmq.producer.FcmNotificationProducer;
import com.aisip.OnO.backend.config.rabbitmq.producer.ProblemAnalysisProducer;
import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ProblemReviewReminderServiceTest {

    @Autowired
    private ProblemReviewReminderService service;

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @MockBean
    private FcmService fcmService;

    @MockBean
    private FcmNotificationProducer fcmNotificationProducer;

    @MockBean
    private ProblemAnalysisProducer problemAnalysisProducer;

    @MockBean
    private FileUploadService fileUploadService;

    @MockBean
    private RateLimitService rateLimitService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long userId;
    private User savedUser;

    @BeforeEach
    void setUp() {
        User user = RandomUserGenerator.createRandomUser();
        savedUser = userRepository.save(user);
        userId = savedUser.getId();
    }

    @AfterEach
    void tearDown() {
        fcmTokenRepository.deleteAll();
        reminderRepository.deleteAll();
        userRepository.deleteById(userId);
    }

    // ──────────────────── scheduleForNewProblems ───────────────────

    @Test
    @DisplayName("problem 1개 등록 시 5개의 SCHEDULED row가 sequence 1~5로 생성된다")
    void scheduleForNewProblems_single_creates5Rows() {
        Long problemId = 1001L;
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 22, 0, 0);

        service.scheduleForNewProblems(userId, List.of(
                new ProblemCreatedEvent.ProblemData(problemId, "memo", "ref", createdAt)
        ));

        List<ProblemReviewReminder> rows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .toList();

        assertThat(rows).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(rows.get(i).getSequence()).isEqualTo(i + 1);
            assertThat(rows.get(i).getStatus()).isEqualTo(SCHEDULED);
        }
    }

    @Test
    @DisplayName("problem 2개 등록 시 총 10개의 row가 생성된다")
    void scheduleForNewProblems_batch_creates10Rows() {
        Long problemId1 = 2001L;
        Long problemId2 = 2002L;
        LocalDateTime createdAt = LocalDateTime.now();

        service.scheduleForNewProblems(userId, List.of(
                new ProblemCreatedEvent.ProblemData(problemId1, "memo1", "ref1", createdAt),
                new ProblemCreatedEvent.ProblemData(problemId2, "memo2", "ref2", createdAt)
        ));

        long count = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId1) || r.getProblemId().equals(problemId2))
                .count();

        assertThat(count).isEqualTo(10);
    }

    @Test
    @DisplayName("동일 problemId로 scheduleForNewProblems 2번 호출 시 row는 5개로 유지된다")
    void scheduleForNewProblems_duplicate_skipsExisting() {
        Long problemId = 3001L;
        LocalDateTime createdAt = LocalDateTime.now();
        ProblemCreatedEvent.ProblemData data =
                new ProblemCreatedEvent.ProblemData(problemId, "memo", "ref", createdAt);

        service.scheduleForNewProblems(userId, List.of(data));
        service.scheduleForNewProblems(userId, List.of(data)); // 중복 호출

        long count = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .count();

        assertThat(count).isEqualTo(5);
    }

    // ──────────────────── cancelPendingByProblem ───────────────────

    @Test
    @DisplayName("cancelPendingByProblem 호출 시 SCHEDULED row가 모두 CANCELED로 변경된다")
    void cancelPendingByProblem_cancelsAllScheduledRows() {
        Long problemId = 4001L;
        saveScheduledReminders(userId, problemId, 5);

        service.cancelPendingByProblem(problemId);

        List<ProblemReviewReminder> rows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .toList();

        assertThat(rows).hasSize(5);
        assertThat(rows).allMatch(r -> r.getStatus() == CANCELED);
    }

    // ──────────────────── refreshSnapshot ─────────────────────────

    @Test
    @DisplayName("refreshSnapshot 호출 시 SCHEDULED row의 memo/reference가 갱신된다")
    void refreshSnapshot_updatesScheduledRows() {
        Long problemId = 5001L;
        saveScheduledReminders(userId, problemId, 3);

        service.refreshSnapshot(problemId, "new memo", "new reference");

        List<ProblemReviewReminder> scheduledRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId) && r.getStatus() == SCHEDULED)
                .toList();

        assertThat(scheduledRows).hasSize(3);
        assertThat(scheduledRows).allMatch(r ->
                "new memo".equals(r.getProblemMemoSnapshot()) &&
                "new reference".equals(r.getProblemReferenceSnapshot())
        );
    }

    @Test
    @DisplayName("refreshSnapshot은 CANCELED 상태의 row는 갱신하지 않는다")
    void refreshSnapshot_doesNotUpdateCanceledRows() {
        Long problemId = 5002L;
        // SCHEDULED row 3개 저장 후 취소
        saveScheduledReminders(userId, problemId, 3);
        service.cancelPendingByProblem(problemId);

        service.refreshSnapshot(problemId, "new memo", "new reference");

        List<ProblemReviewReminder> canceledRows = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .toList();

        assertThat(canceledRows).allMatch(r -> r.getStatus() == CANCELED);
        // CANCELED row이므로 snapshot 갱신 쿼리가 영향을 주지 않아야 함 (originalSnapshot 유지)
        assertThat(canceledRows).allMatch(r ->
                !"new memo".equals(r.getProblemMemoSnapshot())
        );
    }

    // ──────────────────── skipDuePendingByProblemSolve ─────────────

    @Test
    @DisplayName("복습 완료 시 practicedAt 이전의 due row는 SKIPPED_BY_COMPLETION, 이후 row는 SCHEDULED 유지")
    void skipDuePendingByProblemSolve_skipsOnlyDueRows() {
        Long problemId = 6001L;
        LocalDateTime base = LocalDateTime.of(2026, 7, 18, 22, 0, 0);
        // sequence 1: D+1 due, sequence 2: D+3 due ~ sequence 5: D+30 due
        List<ProblemReviewReminder> reminders = List.of(
                ProblemReviewReminder.create(userId, problemId, "m", "r", 1, 1, base.plusDays(1)),
                ProblemReviewReminder.create(userId, problemId, "m", "r", 2, 3, base.plusDays(3)),
                ProblemReviewReminder.create(userId, problemId, "m", "r", 3, 7, base.plusDays(7)),
                ProblemReviewReminder.create(userId, problemId, "m", "r", 4, 14, base.plusDays(14)),
                ProblemReviewReminder.create(userId, problemId, "m", "r", 5, 30, base.plusDays(30))
        );
        reminderRepository.saveAll(reminders);

        // D+1 시점에 복습 완료 → sequence 1만 SKIPPED, 나머지는 SCHEDULED
        LocalDateTime practicedAt = base.plusDays(1).plusHours(1);
        service.skipDuePendingByProblemSolve(userId, problemId, practicedAt);

        Map<Integer, ProblemReviewReminderStatus> statusBySeq = reminderRepository.findAll().stream()
                .filter(r -> r.getProblemId().equals(problemId))
                .collect(Collectors.toMap(ProblemReviewReminder::getSequence, ProblemReviewReminder::getStatus));

        assertThat(statusBySeq.get(1)).isEqualTo(SKIPPED_BY_COMPLETION);
        assertThat(statusBySeq.get(2)).isEqualTo(SCHEDULED);
        assertThat(statusBySeq.get(3)).isEqualTo(SCHEDULED);
        assertThat(statusBySeq.get(4)).isEqualTo(SCHEDULED);
        assertThat(statusBySeq.get(5)).isEqualTo(SCHEDULED);
    }

    // ──────────────────── cancelAllByUser ─────────────────────────

    @Test
    @DisplayName("cancelAllByUser 호출 시 사용자의 모든 SCHEDULED row가 CANCELED로 변경된다")
    void cancelAllByUser_cancelsAllScheduledRows() {
        Long problemId = 7001L;
        saveScheduledReminders(userId, problemId, 5);

        service.cancelAllByUser(userId);

        List<ProblemReviewReminder> rows = reminderRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(userId))
                .toList();

        assertThat(rows).hasSize(5);
        assertThat(rows).allMatch(r -> r.getStatus() == CANCELED);
    }

    // ──────────────────── sendDueReminders ────────────────────────

    @Test
    @DisplayName("정상 발송: due row 있고 알림 허용 + FCM 토큰 있으면 fcmService 1회 호출 후 row가 SENT")
    void sendDueReminders_normalCase_sendsFcmAndMarksSent() {
        FcmToken token = fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("test-token"), userId));

        ProblemReviewReminder reminder = saveAndGetDueReminder(userId, 8001L, LocalDateTime.now().minusMinutes(1));

        service.sendDueReminders(LocalDateTime.now());

        verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(userId), any(NotificationRequestDto.class));

        ProblemReviewReminder updated = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SENT);
    }

    @Test
    @DisplayName("FCM 큐 전송 실패 시 row는 SENT가 아니라 FAILED로 변경된다")
    void sendDueReminders_fcmEnqueueFailure_marksFailed() {
        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("test-token"), userId));
        ProblemReviewReminder reminder = saveAndGetDueReminder(userId, 8002L, LocalDateTime.now().minusMinutes(1));
        doThrow(new IllegalStateException("enqueue failed"))
                .when(fcmService).sendNotificationToAllUserDevice(eq(userId), any(NotificationRequestDto.class));

        service.sendDueReminders(LocalDateTime.now());

        ProblemReviewReminder updated = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FAILED);
        assertThat(updated.getLastErrorMessage()).isEqualTo("enqueue failed");
    }

    @Test
    @DisplayName("notificationEnabled=false인 사용자의 due row는 발송하지 않는다")
    void sendDueReminders_notificationDisabled_skips() {
        savedUser.updateNotificationEnabled(false);
        userRepository.save(savedUser);

        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("test-token"), userId));
        ProblemReviewReminder reminder = saveAndGetDueReminder(userId, 9001L, LocalDateTime.now().minusMinutes(1));

        service.sendDueReminders(LocalDateTime.now());

        verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());

        ProblemReviewReminder unchanged = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    @DisplayName("FCM 토큰이 없으면 발송하지 않고 row는 SCHEDULED로 유지된다")
    void sendDueReminders_noFcmToken_skips() {
        // 토큰 없음
        ProblemReviewReminder reminder = saveAndGetDueReminder(userId, 10001L, LocalDateTime.now().minusMinutes(1));

        service.sendDueReminders(LocalDateTime.now());

        verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());

        ProblemReviewReminder unchanged = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    @DisplayName("오늘 이미 SENT row가 있는 사용자의 due row는 발송하지 않는다")
    void sendDueReminders_alreadySentToday_skips() {
        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("test-token"), userId));

        // 오늘 이미 SENT된 row를 직접 삽입
        ProblemReviewReminder alreadySent = ProblemReviewReminder.create(
                userId, 11001L, "m", "r", 1, 1, LocalDateTime.now().minusDays(1)
        );
        ProblemReviewReminder savedSent = reminderRepository.save(alreadySent);
        final Long sentId = savedSent.getId();
        final LocalDateTime sentAt = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(tx ->
                reminderRepository.markSent(sentId, sentAt, SENT));

        // 새로운 due row
        ProblemReviewReminder due = saveAndGetDueReminder(userId, 11002L, LocalDateTime.now().minusMinutes(1));

        service.sendDueReminders(LocalDateTime.now());

        verify(fcmService, never()).sendNotificationToAllUserDevice(any(), any());

        ProblemReviewReminder unchanged = reminderRepository.findById(due.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    @DisplayName("같은 사용자의 due row 2개 중 scheduledAt이 빠른 1건만 SENT, 나머지는 SCHEDULED 유지")
    void sendDueReminders_multipledue_sendsOnlyEarliest() {
        fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("test-token"), userId));

        LocalDateTime earlier = LocalDateTime.now().minusMinutes(10);
        LocalDateTime later = LocalDateTime.now().minusMinutes(5);

        ProblemReviewReminder earlierRow = reminderRepository.save(
                ProblemReviewReminder.create(userId, 12001L, "m", "r", 1, 1, earlier)
        );
        ProblemReviewReminder laterRow = reminderRepository.save(
                ProblemReviewReminder.create(userId, 12002L, "m", "r", 1, 1, later)
        );

        service.sendDueReminders(LocalDateTime.now());

        verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(userId), any());

        ProblemReviewReminder updatedEarlier = reminderRepository.findById(earlierRow.getId()).orElseThrow();
        ProblemReviewReminder updatedLater = reminderRepository.findById(laterRow.getId()).orElseThrow();

        assertThat(updatedEarlier.getStatus()).isEqualTo(SENT);
        assertThat(updatedLater.getStatus()).isEqualTo(SCHEDULED);
    }

    @Test
    @DisplayName("user1의 SENT row가 있어도 user2의 due row는 독립적으로 발송된다")
    void sendDueReminders_userIsolation_eachUserIndependent() {
        // user2 생성
        User user2 = RandomUserGenerator.createRandomUser();
        userRepository.save(user2);
        Long userId2 = user2.getId();

        try {
            fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("token-u1"), userId));
            fcmTokenRepository.save(FcmToken.From(new FcmTokenRequestDto("token-u2"), userId2));

            // user1: 오늘 이미 SENT
            ProblemReviewReminder sentRow = reminderRepository.save(
                    ProblemReviewReminder.create(userId, 13001L, "m", "r", 1, 1, LocalDateTime.now().minusDays(1))
            );
            final Long sentRowId = sentRow.getId();
            final LocalDateTime now1 = LocalDateTime.now();
            transactionTemplate.executeWithoutResult(tx ->
                    reminderRepository.markSent(sentRowId, now1, SENT));

            // user2: due row
            ProblemReviewReminder dueRow = saveAndGetDueReminder(userId2, 13002L, LocalDateTime.now().minusMinutes(1));

            service.sendDueReminders(LocalDateTime.now());

            // user1은 skip, user2는 발송
            verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(userId2), any());
            verify(fcmService, never()).sendNotificationToAllUserDevice(eq(userId), any());

            ProblemReviewReminder updatedDue = reminderRepository.findById(dueRow.getId()).orElseThrow();
            assertThat(updatedDue.getStatus()).isEqualTo(SENT);

        } finally {
            fcmTokenRepository.findAllByUserId(userId2).forEach(t -> fcmTokenRepository.deleteByToken(t.getToken()));
            reminderRepository.findAll().stream()
                    .filter(r -> r.getUserId().equals(userId2))
                    .forEach(r -> reminderRepository.deleteById(r.getId()));
            userRepository.deleteById(userId2);
        }
    }

    @Test
    @DisplayName("sendDueReminders 호출 시 11분 전 SENDING row는 FAILED로 복구된다")
    void sendDueReminders_stuckSendingRow_recoveredToFailed() {
        ProblemReviewReminder reminder = reminderRepository.save(
                ProblemReviewReminder.create(userId, 14001L, "m", "r", 1, 1, LocalDateTime.now().minusMinutes(30))
        );

        // SENDING 상태로 강제 변경
        final Long reminderId = reminder.getId();
        transactionTemplate.executeWithoutResult(tx ->
                reminderRepository.tryUpdateStatus(reminderId, SCHEDULED, SENDING, LocalDateTime.now()));

        // @LastModifiedDate 자동 갱신을 우회하기 위해 EntityManager 네이티브 쿼리로 updatedAt 조작
        final LocalDateTime stuckUpdatedAt = LocalDateTime.now().minusMinutes(11);
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.createNativeQuery(
                        "UPDATE problem_review_reminder SET updated_at = ? WHERE id = ?"
                ).setParameter(1, stuckUpdatedAt).setParameter(2, reminderId).executeUpdate());

        service.sendDueReminders(LocalDateTime.now());

        ProblemReviewReminder recovered = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(FAILED);
        assertThat(recovered.getLastErrorMessage()).isEqualTo("stuck recovery");
    }

    // ──────────────────── helper ──────────────────────────────────

    private void saveScheduledReminders(Long userId, Long problemId, int count) {
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        List<Integer> intervals = List.of(1, 3, 7, 14, 30);
        for (int i = 0; i < count; i++) {
            reminderRepository.save(ProblemReviewReminder.create(
                    userId, problemId, "memo", "ref",
                    i + 1, intervals.get(i), base.plusDays(intervals.get(i))
            ));
        }
    }

    private ProblemReviewReminder saveAndGetDueReminder(Long userId, Long problemId, LocalDateTime scheduledAt) {
        return reminderRepository.save(
                ProblemReviewReminder.create(userId, problemId, "memo", "ref", 1, 1, scheduledAt)
        );
    }
}
