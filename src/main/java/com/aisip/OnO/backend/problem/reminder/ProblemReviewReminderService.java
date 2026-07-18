package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.*;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReviewReminderService {

    private static final int STUCK_TIMEOUT_MINUTES = 10;
    private static final List<ProblemReviewReminderStatus> PENDING_STATUSES = List.of(SCHEDULED, SENDING);

    private final ProblemReviewReminderRepository repository;
    private final ProblemReviewReminderPolicy policy;
    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;

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
        repository.refreshSnapshot(problemId, memo, reference, SCHEDULED);
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

    @Transactional
    public void sendDueReminders(LocalDateTime now) {
        recoverStuckRows(now);

        List<ProblemReviewReminder> dueRows = repository.findDueReminders(SCHEDULED, now);
        if (dueRows.isEmpty()) return;

        Map<Long, List<ProblemReviewReminder>> byUser = dueRows.stream()
                .collect(Collectors.groupingBy(ProblemReviewReminder::getUserId));

        LocalDate today = now.toLocalDate();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        for (Map.Entry<Long, List<ProblemReviewReminder>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<ProblemReviewReminder> userRows = entry.getValue().stream()
                    .sorted(Comparator.comparing(ProblemReviewReminder::getScheduledAt))
                    .toList();

            if (repository.hasSentTodayForUser(userId, SENT, startOfDay, endOfDay)) {
                continue;
            }

            ProblemReviewReminder candidate = userRows.get(0);

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isNotificationEnabled()) {
                continue;
            }

            if (fcmTokenRepository.findAllByUserId(userId).isEmpty()) {
                log.debug("[ReviewReminder] FCM 토큰 없음 - userId: {}", userId);
                continue;
            }

            int claimed = repository.tryUpdateStatus(candidate.getId(), SCHEDULED, SENDING);
            if (claimed == 0) {
                log.warn("[ReviewReminder] 선점 실패 - reminderId: {}", candidate.getId());
                continue;
            }

            try {
                NotificationRequestDto dto = policy.buildNotification(candidate);
                fcmService.sendNotificationToAllUserDevice(userId, dto);
                repository.markSent(candidate.getId(), now, SENT);
                log.info("[ReviewReminder] 발송 완료 - userId: {}, reminderId: {}, sequence: {}",
                        userId, candidate.getId(), candidate.getSequence());
            } catch (Exception e) {
                repository.markFailed(candidate.getId(), truncateErrorMessage(e.getMessage()), FAILED);
                log.error("[ReviewReminder] 발송 실패 - userId: {}, reminderId: {}", userId, candidate.getId(), e);
            }
        }
    }

    private void recoverStuckRows(LocalDateTime now) {
        LocalDateTime stuckBefore = now.minusMinutes(STUCK_TIMEOUT_MINUTES);
        int recovered = repository.recoverStuckRows(SENDING, FAILED, stuckBefore);
        if (recovered > 0) {
            log.warn("[ReviewReminder] SENDING stuck row 복구: {}건", recovered);
        }
    }

    private String truncateErrorMessage(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
