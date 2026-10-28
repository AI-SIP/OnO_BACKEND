package com.aisip.OnO.backend.problem.quartz;

import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.NotificationType;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ReviewDueNotificationJob extends QuartzJobBean {

    private static final int REENGAGEMENT_THRESHOLD_DAYS = 7;
    private static final int REENGAGEMENT_INTERVAL_DAYS = 5;
    private static final int LONG_INACTIVE_THRESHOLD_DAYS = 30;
    private static final int LONG_INACTIVE_INTERVAL_DAYS = 30;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmService fcmService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        LocalDateTime reengagementCutoff = today.minusDays(REENGAGEMENT_THRESHOLD_DAYS).atStartOfDay(seoul).toLocalDateTime();
        LocalDateTime longInactiveCutoff = today.minusDays(LONG_INACTIVE_THRESHOLD_DAYS).atStartOfDay(seoul).toLocalDateTime();

        // 복습 예정 문제 수를 알리던 9시 요약 푸시(흐름 1)는 문제별 복습 리마인더로 대신해 보내지 않는다.
        sendReengagementNotifications(today, reengagementCutoff, longInactiveCutoff);
        sendLongInactiveReengagementNotifications(today, longInactiveCutoff);
    }

    // 흐름 2: 일정 기간 미접속 유저에게 재참여 알림 (due 문제 여부 무관)
    private void sendReengagementNotifications(LocalDate today, LocalDateTime reengagementCutoff, LocalDateTime inactiveCutoff) {
        LocalDate notificationCutoff = today.minusDays(REENGAGEMENT_INTERVAL_DAYS);

        List<User> inactiveUsers = userRepository.findUsersForReengagement(
                reengagementCutoff, inactiveCutoff, notificationCutoff);

        if (inactiveUsers.isEmpty()) return;

        List<Long> notifiedUserIds = new ArrayList<>();

        for (User user : inactiveUsers) {
            if (!user.isNotificationEnabled()) continue;
            if (sendSafely(user.getId(),
                    new NotificationRequestDto("", "오랜만이에요!",
                            "오답노트를 펼칠 시간이에요. 복습하러 돌아와보세요!",
                            Map.of("type", NotificationType.REENGAGEMENT)))) {
                notifiedUserIds.add(user.getId());
            }
        }

        if (!notifiedUserIds.isEmpty()) {
            userRepository.bulkUpdateLastNotifiedAt(notifiedUserIds, today);
        }
        log.info("[ReviewDue] 재참여 알림 발송: {}명", notifiedUserIds.size());
    }

    // 흐름 3: 30일 초과 미접속 유저에게 월 1회 알림
    private void sendLongInactiveReengagementNotifications(LocalDate today, LocalDateTime longInactiveCutoff) {
        LocalDate notificationCutoff = today.minusDays(LONG_INACTIVE_INTERVAL_DAYS);

        List<User> longInactiveUsers = userRepository.findUsersForLongInactiveReengagement(
                longInactiveCutoff, notificationCutoff);

        if (longInactiveUsers.isEmpty()) return;

        List<Long> notifiedUserIds = new ArrayList<>();

        for (User user : longInactiveUsers) {
            if (!user.isNotificationEnabled()) continue;
            if (sendSafely(user.getId(),
                    new NotificationRequestDto("", "오답노트가 기다리고 있어요",
                            "한동안 자리를 비우셨네요. 다시 시작하기 딱 좋은 날이에요!",
                            Map.of("type", NotificationType.REENGAGEMENT_MONTHLY)))) {
                notifiedUserIds.add(user.getId());
            }
        }

        if (!notifiedUserIds.isEmpty()) {
            userRepository.bulkUpdateLastNotifiedAt(notifiedUserIds, today);
        }
        log.info("[ReviewDue] 장기 미접속 알림 발송: {}명", notifiedUserIds.size());
    }

    /**
     * 한 사용자에게 보내다 실패해도 배치 전체를 멈추면 안 된다.
     * 예전에는 발송 예외가 그대로 올라와 뒤에 남은 사용자들이 그날 알림을 통째로 놓쳤다.
     *
     * @return 발송을 시도해 예외 없이 끝났으면 true. 실패한 사용자는 발송 이력을 남기지 않아
     * 다음 실행에서 다시 대상이 된다.
     */
    private boolean sendSafely(Long userId, NotificationRequestDto request) {
        try {
            fcmService.sendNotificationToAllUserDevice(userId, request);
            return true;
        } catch (Exception e) {
            log.warn("[ReviewDue] 알림 발송 실패 - userId: {}, type: {}, reason: {}",
                    userId, request.data().get("type"), e.getMessage());
            return false;
        }
    }
}
