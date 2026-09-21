package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.*;

/**
 * 복습 알림 한 건을 선점하고 큐에 넣은 뒤 결과를 기록한다.
 *
 * <p>선점, 큐 적재, 결과 기록을 한 트랜잭션에 묶으면 적재 예외가 트랜잭션을 rollback-only 로 만들어
 * SENDING 선점과 FAILED 기록이 함께 사라지고, 커밋 예외가 폴링 루프까지 올라가 뒤 사용자들이 발송되지 않았다.
 * 그래서 선점과 결과 기록은 각각 짧은 새 트랜잭션으로 커밋하고, 큐 적재는 트랜잭션 밖에서 한다.
 * 브로커 응답을 기다리는 동안 DB 커넥션을 잡고 있지도 않는다.
 *
 * <p>{@link #send} 는 예외를 던지지 않는다. 한 건의 실패가 배치 전체를 멈추면 안 되기 때문이다.
 */
@Slf4j
@Component
public class ProblemReviewReminderSender {

    private final ProblemReviewReminderRepository repository;
    private final ProblemReviewReminderPolicy policy;
    private final FcmService fcmService;
    private final TransactionTemplate newTransaction;

    public ProblemReviewReminderSender(ProblemReviewReminderRepository repository,
                                       ProblemReviewReminderPolicy policy,
                                       FcmService fcmService,
                                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.policy = policy;
        this.fcmService = fcmService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void send(ProblemReviewReminder candidate, LocalDateTime now) {
        Long userId = candidate.getUserId();
        Long reminderId = candidate.getId();

        try {
            Integer claimed = newTransaction.execute(status ->
                    repository.tryUpdateStatus(reminderId, SCHEDULED, SENDING, now));
            if (claimed == null || claimed == 0) {
                log.warn("[ReviewReminder] 선점 실패 - reminderId: {}", reminderId);
                return;
            }
        } catch (Exception e) {
            log.error("[ReviewReminder] 선점 중 오류 - userId: {}, reminderId: {}", userId, reminderId, e);
            return;
        }

        try {
            NotificationRequestDto dto = policy.buildNotification(candidate);
            fcmService.sendNotificationToAllUserDevice(userId, dto);
        } catch (Exception e) {
            log.error("[ReviewReminder] 발송 실패 - userId: {}, reminderId: {}", userId, reminderId, e);
            recordFailure(userId, reminderId, e);
            return;
        }

        try {
            newTransaction.executeWithoutResult(status -> repository.markSent(reminderId, now, SENT));
            log.info("[ReviewReminder] 발송 완료 - userId: {}, reminderId: {}, sequence: {}",
                    userId, reminderId, candidate.getSequence());
        } catch (Exception e) {
            // 이미 큐에 들어갔으므로 FAILED 로 덮지 않는다. SENDING 으로 남은 행은 stuck 복구가 정리하고,
            // SCHEDULED 로 되돌리지 않으니 같은 알림이 다시 나가지도 않는다.
            log.error("[ReviewReminder] 적재 후 SENT 기록 실패 - userId: {}, reminderId: {}", userId, reminderId, e);
        }
    }

    private void recordFailure(Long userId, Long reminderId, Exception cause) {
        String errorMsg = cause.getMessage();
        if (errorMsg != null && errorMsg.length() > 500) errorMsg = errorMsg.substring(0, 500);
        String truncated = errorMsg;
        try {
            newTransaction.executeWithoutResult(status -> repository.markFailed(reminderId, truncated, FAILED));
        } catch (Exception e) {
            // SENDING 으로 남은 행은 stuck 복구가 FAILED 로 넘긴다.
            log.error("[ReviewReminder] FAILED 기록 실패 - userId: {}, reminderId: {}", userId, reminderId, e);
        }
    }
}
