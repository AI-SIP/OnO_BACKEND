package com.aisip.OnO.backend.studyroom.quartz;

import com.aisip.OnO.backend.studyroom.service.StudyRoomWeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyRoomWeeklyReportJob implements Job {

    private final StudyRoomWeeklyReportService reportService;

    @Override
    public void execute(JobExecutionContext context) {
        reportService.createPreviousWeekReports();
    }
}
