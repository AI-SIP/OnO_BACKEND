package com.aisip.OnO.backend.studyroom.quartz;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudyRoomScheduler {

    @Bean
    public JobDetail studyRoomWeeklyReportJobDetail() {
        return JobBuilder.newJob(StudyRoomWeeklyReportJob.class)
                .withIdentity("studyRoomWeeklyReportJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger studyRoomWeeklyReportTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(studyRoomWeeklyReportJobDetail())
                .withIdentity("studyRoomWeeklyReportTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 ? * MON").inTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul")))
                .build();
    }

}
