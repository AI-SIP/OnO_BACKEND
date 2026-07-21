package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.*;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemReviewReminderSender {

    private final ProblemReviewReminderRepository repository;
    private final ProblemReviewReminderPolicy policy;
    private final FcmService fcmService;

    @Transactional(propagation = REQUIRES_NEW)
    public void send(ProblemReviewReminder candidate, LocalDateTime now) {
        Long userId = candidate.getUserId();

        int claimed = repository.tryUpdateStatus(candidate.getId(), SCHEDULED, SENDING, now);
        if (claimed == 0) {
            log.warn("[ReviewReminder] 선점 실패 - reminderId: {}", candidate.getId());
            return;
        }

        try {
            NotificationRequestDto dto = policy.buildNotification(candidate);
            fcmService.sendNotificationToAllUserDevice(userId, dto);
            repository.markSent(candidate.getId(), now, SENT);
            log.info("[ReviewReminder] 발송 완료 - userId: {}, reminderId: {}, sequence: {}",
                    userId, candidate.getId(), candidate.getSequence());
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) errorMsg = errorMsg.substring(0, 500);
            repository.markFailed(candidate.getId(), errorMsg, FAILED);
            log.error("[ReviewReminder] 발송 실패 - userId: {}, reminderId: {}", userId, candidate.getId(), e);
        }
    }
}
