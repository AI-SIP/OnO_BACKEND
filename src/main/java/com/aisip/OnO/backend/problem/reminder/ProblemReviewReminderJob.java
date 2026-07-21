package com.aisip.OnO.backend.problem.reminder;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
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
