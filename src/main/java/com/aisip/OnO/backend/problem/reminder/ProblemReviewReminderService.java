package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.*;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReviewReminderService {

    private static final int STUCK_TIMEOUT_MINUTES = 10;
    private static final int DUE_REMINDER_BATCH_SIZE = 500;
    private static final List<ProblemReviewReminderStatus> PENDING_STATUSES = List.of(SCHEDULED, SENDING);

    private final ProblemReviewReminderRepository repository;
    private final ProblemReviewReminderPolicy policy;
    private final FcmService fcmService;
    private final ProblemReviewReminderSender sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleProblemCreated(ProblemCreatedEvent event) {
        try {
            scheduleForNewProblems(event.userId(), event.problems());
        } catch (Exception e) {
            log.error("[ReviewReminder] 자동 알림 예약 실패 - userId: {}", event.userId(), e);
        }
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void scheduleForNewProblems(Long userId, List<ProblemCreatedEvent.ProblemData> problems) {
        List<Integer> intervals = policy.getIntervals();
        List<ProblemReviewReminder> reminders = new ArrayList<>();

        for (ProblemCreatedEvent.ProblemData data : problems) {
            for (int i = 0; i < intervals.size(); i++) {
                int sequence = i + 1;
                int intervalDays = intervals.get(i);
                if (repository.existsByProblemIdAndSequence(data.problemId(), sequence)) {
                    continue;
                }
                LocalDateTime scheduledAt = policy.calculateScheduledAt(data.createdAt(), intervalDays);
                reminders.add(ProblemReviewReminder.create(
                        userId, data.problemId(), data.memo(), data.reference(),
                        sequence, intervalDays, scheduledAt
                ));
            }
        }

        if (!reminders.isEmpty()) {
            repository.saveAll(reminders);
            log.info("[ReviewReminder] 자동 알림 예약 생성 - userId: {}, 문제 {}개, row {}개",
                    userId, problems.size(), reminders.size());
        }
    }

    @Transactional
    public void cancelPendingByProblem(Long problemId) {
        int count = repository.cancelByProblem(problemId, CANCELED, PENDING_STATUSES);
        if (count > 0) {
            log.info("[ReviewReminder] 문제 삭제로 알림 취소 - problemId: {}, {}건", problemId, count);
        }
    }

    @Transactional
    public void refreshSnapshot(Long problemId, String memo, String reference) {
        repository.refreshSnapshot(
                problemId,
                ProblemReviewReminder.truncateSnapshot(memo),
                ProblemReviewReminder.truncateSnapshot(reference),
                SCHEDULED
        );
    }

    @Transactional
    public void skipDuePendingByProblemSolve(Long userId, Long problemId, LocalDateTime practicedAt) {
        int count = repository.skipDuePendingByProblem(problemId, SKIPPED_BY_COMPLETION, SCHEDULED, practicedAt);
        if (count > 0) {
            log.info("[ReviewReminder] 복습 완료로 due 알림 skip - userId: {}, problemId: {}, {}건",
                    userId, problemId, count);
        }
    }

    @Transactional
    public void cancelAllByUser(Long userId) {
        int count = repository.cancelAllByUser(userId, CANCELED, PENDING_STATUSES);
        if (count > 0) {
            log.info("[ReviewReminder] 사용자 탈퇴로 알림 취소 - userId: {}, {}건", userId, count);
        }
    }

    public void sendDueReminders(LocalDateTime now) {
        recoverStuckRows(now);

        LocalDate today = now.toLocalDate();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<ProblemReviewReminder> dueRows = repository.findDueReminders(
                SCHEDULED, now, SENT, startOfDay, endOfDay, PageRequest.of(0, DUE_REMINDER_BATCH_SIZE)
        );
        if (dueRows.isEmpty()) return;

        Map<Long, List<ProblemReviewReminder>> byUser = dueRows.stream()
                .collect(Collectors.groupingBy(ProblemReviewReminder::getUserId));

        for (Map.Entry<Long, List<ProblemReviewReminder>> entry : byUser.entrySet()) {
            ProblemReviewReminder candidate = entry.getValue().stream()
                    .min(Comparator.comparing(ProblemReviewReminder::getScheduledAt))
                    .orElseThrow();
            sender.send(candidate, now);
        }
    }

    private void recoverStuckRows(LocalDateTime now) {
        LocalDateTime stuckBefore = now.minusMinutes(STUCK_TIMEOUT_MINUTES);
        int recovered = repository.recoverStuckRows(SENDING, FAILED, stuckBefore);
        if (recovered > 0) {
            log.warn("[ReviewReminder] SENDING stuck row 복구: {}건", recovered);
        }
    }

}
