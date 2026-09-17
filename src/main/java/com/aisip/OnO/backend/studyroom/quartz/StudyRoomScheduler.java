package com.aisip.OnO.backend.studyroom.quartz;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

/**
 * 스터디룸 주간 리포트 잡을 스케줄러에 등록한다.
 *
 * <p>예전에는 JobDetail 과 Trigger 를 {@code @Bean} 으로 선언만 했다. {@code @Bean} 을 모아 등록하는 건
 * Spring Boot 자동 구성의 스케줄러인데, {@code QuartzConfig} 가 SchedulerFactoryBean 을 직접 만들어
 * 자동 구성이 물러나 한 번도 등록되지 않았다. 다른 스케줄러와 같은 방식으로 직접 등록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyRoomScheduler {

    // 이름은 기존 @Bean 선언과 같게 두고 그룹은 지정하지 않는다(DEFAULT).
    private static final String JOB_NAME = "studyRoomWeeklyReportJob";
    private static final String TRIGGER_NAME = "studyRoomWeeklyReportTrigger";
    private static final String CRON = "0 0 8 ? * MON";

    private final Scheduler scheduler;

    @PostConstruct
    public void schedule() {
        try {
            JobDetail jobDetail = JobBuilder.newJob(StudyRoomWeeklyReportJob.class)
                    .withIdentity(JOB_NAME)
                    .storeDurably()
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(TRIGGER_NAME)
                    .withSchedule(CronScheduleBuilder.cronSchedule(CRON).inTimeZone(TimeZone.getTimeZone("Asia/Seoul")))
                    .build();

            scheduler.addJob(jobDetail, true);

            if (scheduler.checkExists(trigger.getKey())) {
                scheduler.rescheduleJob(trigger.getKey(), trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }

            log.info("[StudyRoomWeeklyReport] 주간 리포트 스케줄 등록 완료 - cron: {} (Asia/Seoul)", CRON);
        } catch (SchedulerException e) {
            log.error("[StudyRoomWeeklyReport] 주간 리포트 스케줄 등록 실패", e);
        }
    }
}
