package com.aisip.OnO.backend.problem.reminder;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 5분마다 due 복습 알림을 내보내는 폴링 잡.
 *
 * <p>한 실행이 5분을 넘기면 다음 실행이 겹쳐 같은 사용자를 두 번 처리할 수 있어 중첩을 막는다.
 * 다만 스케줄러가 클러스터 모드가 아니라(application-prod.yml 의 isClustered: false) blue/green
 * 두 인스턴스 사이의 동시 폴링은 이것으로 막히지 않는다. 그건 findDueReminders 의
 * "오늘 이미 처리한 사용자" 판정이 막는다.
 */
@Slf4j
@DisallowConcurrentExecution
public class ProblemReviewReminderJob extends QuartzJobBean {

    @Autowired
    private ProblemReviewReminderService reminderService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        log.info("[ReviewReminder] polling 시작 - {}", now);
        try {
            reminderService.sendDueReminders(now);
        } catch (Exception e) {
            log.error("[ReviewReminder] polling 실패", e);
        }
    }
}
