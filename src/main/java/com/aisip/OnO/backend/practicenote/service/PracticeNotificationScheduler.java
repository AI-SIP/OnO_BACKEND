
package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeNotificationScheduler {

    private final Scheduler scheduler;

    public void schedulePracticeNotification(Long userId, Long practiceId, String practiceTitle, PracticeNotificationRegisterDto dto) {
        try {
            JobDetail jobDetail = JobBuilder.newJob(PracticeNotificationJob.class)
                    .withIdentity("practice-" + practiceId, "practice-reminder")
                    .usingJobData("userId", userId)
                    .usingJobData("practiceId", practiceId)
                    .usingJobData("practiceTitle", practiceTitle)
                    .build();

            String cron = convertDtoToCron(dto);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + practiceId, "practice-reminder")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .forJob(jobDetail)
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("스케줄 등록 실패", e);
        }
    }

    private String convertDtoToCron(PracticeNotificationRegisterDto dto) {
        int hour = dto.hour();
        int minute = dto.minute();

        if ("daily".equalsIgnoreCase(dto.repeatType())) {
            // 매일 지정된 시각에 실행
            return String.format("0 %d %d ? * *", minute, hour);
        } else if ("weekly".equalsIgnoreCase(dto.repeatType()) && dto.weekDays() != null && !dto.weekDays().isEmpty()) {
            // 선택한 요일에만 지정된 시각에 실행 (e.g. MON,WED,FRI)
            String dayString = dto.weekDays().stream()
                    .map(this::convertDayToQuartz)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("*");

            return String.format("0 %d %d ? * %s", minute, hour, dayString);
        }

        // fallback (매일)
        return String.format("0 %d %d ? * *", minute, hour);
    }

    private String convertDayToQuartz(int day) {
        return switch (day) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            case 7 -> "SUN";
            default -> throw new IllegalArgumentException("Invalid weekday: " + day);
        };
    }
}
