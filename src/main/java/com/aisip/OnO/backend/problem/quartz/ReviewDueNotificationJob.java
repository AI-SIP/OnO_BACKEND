package com.aisip.OnO.backend.problem.quartz;

import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problem.repository.ReviewDueSummary;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ReviewDueNotificationJob extends QuartzJobBean {

    private static final int REENGAGEMENT_THRESHOLD_DAYS = 7;
    private static final int REENGAGEMENT_INTERVAL_DAYS = 5;
    private static final int LONG_INACTIVE_THRESHOLD_DAYS = 30;
    private static final int LONG_INACTIVE_INTERVAL_DAYS = 30;

    @Autowired
    private ProblemRepository problemRepository;

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

        sendReviewNotifications(today, reengagementCutoff);
        sendReengagementNotifications(today, reengagementCutoff, longInactiveCutoff);
        sendLongInactiveReengagementNotifications(today, longInactiveCutoff);
    }

    // 흐름 1: 복습 예정일이 된 문제가 있는 활성 유저에게 복습 알림
    private void sendReviewNotifications(LocalDate today, LocalDateTime reengagementCutoff) {
        List<ReviewDueSummary> summaries = problemRepository.findReviewDueSummaryByDate(today);
        if (summaries.isEmpty()) return;

        Map<Long, Long> dueCountByUserId = summaries.stream()
                .collect(Collectors.toMap(ReviewDueSummary::getUserId, ReviewDueSummary::getDueCount));

        List<User> users = userRepository.findAllById(new ArrayList<>(dueCountByUserId.keySet()));
        List<Long> notifiedUserIds = new ArrayList<>();

        for (User user : users) {
            // 3일 이상 미접속 유저는 재참여 흐름으로 처리
            if (user.getLastActiveAt() == null || user.getLastActiveAt().isBefore(reengagementCutoff)) {
                continue;
            }
            // 오늘 이미 알림 받은 경우 스킵
            if (today.equals(user.getLastNotifiedAt())) {
                continue;
            }

            long dueCount = dueCountByUserId.get(user.getId());
            fcmService.sendNotificationToAllUserDevice(user.getId(),
                    new NotificationRequestDto("", "오늘의 복습 알림",
                            "오늘 복습할 문제가 " + dueCount + "개 있어요!",
                            Map.of("type", "review_due")));
            notifiedUserIds.add(user.getId());
        }

        if (!notifiedUserIds.isEmpty()) {
            userRepository.bulkUpdateLastNotifiedAt(notifiedUserIds, today);
        }
        log.info("[ReviewDue] 복습 알림 발송: {}명", notifiedUserIds.size());
    }

    // 흐름 2: 일정 기간 미접속 유저에게 재참여 알림 (due 문제 여부 무관)
    private void sendReengagementNotifications(LocalDate today, LocalDateTime reengagementCutoff, LocalDateTime inactiveCutoff) {
        LocalDate notificationCutoff = today.minusDays(REENGAGEMENT_INTERVAL_DAYS);

        List<User> inactiveUsers = userRepository.findUsersForReengagement(
                reengagementCutoff, inactiveCutoff, notificationCutoff);

        if (inactiveUsers.isEmpty()) return;

        List<Long> notifiedUserIds = new ArrayList<>();

        for (User user : inactiveUsers) {
            fcmService.sendNotificationToAllUserDevice(user.getId(),
                    new NotificationRequestDto("", "오랜만이에요!",
                            "오답노트를 펼칠 시간이에요. 복습하러 돌아와보세요!",
                            Map.of("type", "reengagement")));
            notifiedUserIds.add(user.getId());
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
            fcmService.sendNotificationToAllUserDevice(user.getId(),
                    new NotificationRequestDto("", "오답노트가 기다리고 있어요",
                            "한동안 자리를 비우셨네요. 다시 시작하기 딱 좋은 날이에요!",
                            Map.of("type", "reengagement_monthly")));
            notifiedUserIds.add(user.getId());
        }

        if (!notifiedUserIds.isEmpty()) {
            userRepository.bulkUpdateLastNotifiedAt(notifiedUserIds, today);
        }
        log.info("[ReviewDue] 장기 미접속 알림 발송: {}명", notifiedUserIds.size());
    }
}