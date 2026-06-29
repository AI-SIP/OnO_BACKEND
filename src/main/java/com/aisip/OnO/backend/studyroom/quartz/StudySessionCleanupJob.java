package com.aisip.OnO.backend.studyroom.quartz;

import com.aisip.OnO.backend.studyroom.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class StudySessionCleanupJob implements Job {

    private final StudySessionService sessionService;

    @Override
    public void execute(JobExecutionContext context) {
        LocalDateTime now = LocalDateTime.now();
        sessionService.closeExpiredSessions(now.minusHours(12), now);
    }
}
