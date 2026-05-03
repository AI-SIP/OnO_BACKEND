package com.aisip.OnO.backend.problem.quartz;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewDueNotificationScheduler {

    private static final String JOB_NAME = "review-due-notification";
    private static final String JOB_GROUP = "review";
    private static final String TRIGGER_NAME = "review-due-trigger";
    private static final String CRON = "0 0 9 ? * *";

    private final Scheduler scheduler;

    @PostConstruct
    public void schedule() {
        try {
            JobDetail jobDetail = JobBuilder.newJob(ReviewDueNotificationJob.class)
                    .withIdentity(JOB_NAME, JOB_GROUP)
                    .storeDurably(true)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_NAME, JOB_GROUP)
                    .withSchedule(
                            CronScheduleBuilder.cronSchedule(CRON)
                                    .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
                    )
                    .forJob(jobDetail)
                    .build();

            scheduler.addJob(jobDetail, true);

            if (scheduler.checkExists(trigger.getKey())) {
                scheduler.rescheduleJob(trigger.getKey(), trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }

            log.info("[ReviewDue] 복습 알림 스케줄 등록 완료 - cron: {} (Asia/Seoul)", CRON);
        } catch (SchedulerException e) {
            log.error("[ReviewDue] 복습 알림 스케줄 등록 실패", e);
        }
    }
}