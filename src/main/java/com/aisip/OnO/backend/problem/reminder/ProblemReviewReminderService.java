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
import java.util.Collection;
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
    private static final int OVERDUE_EXPIRE_DAYS = 3;
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

    /**
     * 문제 삭제로 남은 예약을 취소한다.
     *
     * <p>취소 대상을 먼저 읽고 기본 키로 UPDATE 한다. {@code problem_id} 조건으로 바로 UPDATE 하면
     * next-key lock 이 인덱스 끝의 갭까지 잡아서, 폴더 삭제가 커밋될 때까지 다른 계정의 문제 등록이
     * 전부 예약 INSERT 에서 막혔다. (#319, {@link ProblemReviewReminderRepository#cancelByIdIn})
     */
    @Transactional
    public void cancelPendingByProblem(Long problemId) {
        List<Long> pendingIds = repository.findPendingIdsByProblem(problemId, PENDING_STATUSES);
        if (pendingIds.isEmpty()) {
            return;
        }

        int count = repository.cancelByIdIn(pendingIds, CANCELED);
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

    public void sendDueReminders(LocalDateTime now) {
        recoverStuckRows(now);
        expireOverdueRows(now);

        // 예약 시각을 창 안으로 맞춰 두어도 밀린 예약은 창 밖에서 due 가 된다.
        // 상한에 걸려 넘어온 예약이나 실패 뒤 남은 예약이 새벽에 나가지 않도록 보낼 때도 시각을 본다.
        if (!policy.isWithinSendWindow(now)) {
            return;
        }

        List<ProblemReviewReminder> dueRows = repository.findDueReminders(
                SCHEDULED, now, PageRequest.of(0, DUE_REMINDER_BATCH_SIZE)
        );
        if (dueRows.isEmpty()) return;

        // 같은 폴링에 걸린 한 사용자의 예약은 푸시 하나로 묶는다. 몰아서 등록한 문제가 같은 시각에
        // 한꺼번에 due 가 되면 푸시가 연달아 쏟아지기 때문이다.
        Map<Long, List<ProblemReviewReminder>> byUser = dueRows.stream()
                .collect(Collectors.groupingBy(ProblemReviewReminder::getUserId));
        Map<Long, Long> todayPushes = countTodayPushes(byUser.keySet(), now.toLocalDate());

        for (Map.Entry<Long, List<ProblemReviewReminder>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            // 상한에 걸린 예약은 SCHEDULED 로 남아 다음 날 첫 폴링에 묶여 나가거나 기한이 지나 만료된다.
            if (todayPushes.getOrDefault(userId, 0L) >= policy.getDailyPushLimit()) {
                continue;
            }
            sender.send(userId, entry.getValue(), now);
        }
    }

    private Map<Long, Long> countTodayPushes(Collection<Long> userIds, LocalDate today) {
        return repository.countTodayPushesByUser(
                        userIds, SENT, SENDING, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(ReminderPushCount::getUserId, ReminderPushCount::getPushCount));
    }

    /**
     * 기한이 한참 지난 예약을 접는다.
     *
     * <p>하루 푸시 상한에 걸렸거나, 알림을 껐거나, 토큰이 없는 사용자의 예약은 {@code SCHEDULED} 로 남는다.
     * 이걸 정리하지 않으면 영원히 밀린 채로 남는다. 사흘이 지난 복습 알림은 지금 보내도 의미가 없으니 보내지 않는다.
     */
    private void expireOverdueRows(LocalDateTime now) {
        LocalDateTime expireBefore = now.minusDays(OVERDUE_EXPIRE_DAYS);
        int expired = repository.expireOverdueRows(SCHEDULED, EXPIRED, expireBefore, now);
        if (expired > 0) {
            log.info("[ReviewReminder] 기한이 지난 예약 만료: {}건", expired);
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
