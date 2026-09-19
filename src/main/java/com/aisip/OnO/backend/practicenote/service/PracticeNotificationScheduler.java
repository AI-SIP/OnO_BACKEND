
package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                    .storeDurably(true)
                    .requestRecovery(true)
                    .build();

            String cron = convertDtoToCron(dto);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + practiceId, "practice-reminder")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .forJob(jobDetail)
                    .build();

            scheduler.addJob(jobDetail, true);
            scheduler.scheduleJob(trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("스케줄 등록 실패", e);
        }
    }

    public void updateNotification(Long userId, Long practiceId, String title, PracticeNotificationRegisterDto dto) {
        deleteNotification(practiceId);
        schedulePracticeNotification(userId, practiceId, title, dto);
    }

    public void deleteNotification(Long practiceId) {
        try {
            JobKey jobKey = JobKey.jobKey("practice-" + practiceId, "practice-reminder");
            scheduler.deleteJob(jobKey);
        } catch (SchedulerException e) {
            throw new RuntimeException("알림 삭제 실패", e);
        }
    }

    private String convertDtoToCron(PracticeNotificationRegisterDto dto) {
        int hour = dto.hour();
        int minute = dto.minute();

        if ("daily".equalsIgnoreCase(dto.repeatType())) {
            // 매일 지정된 시각에 실행
            return String.format("0 %d %d ? * *", minute, hour);
        } else if ("weekly".equalsIgnoreCase(dto.repeatType()) && !dto.isWeeklyWithoutWeekDays()) {
            // 선택한 요일에만 지정된 시각에 실행 (e.g. MON,WED,FRI)
            String dayString = dto.weekDays().stream()
                    .map(this::convertDayToQuartz)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("*");

            return String.format("0 %d %d ? * %s", minute, hour, dayString);
        }

        // 매일 폴백. 여기로 오는 경우는 둘이다.
        // 1) daily/weekly 가 아닌 값(null 포함). 구버전 앱이 repeatType 을 비워 보낸다.
        // 2) 주간 반복인데 요일이 비어 있는 구버전 앱 요청. 신버전 요청은 진입부인
        //    PracticeNoteService 에서 이미 400 으로 걸러지고 여기까지 오지 않는다.
        //    구버전 앱에는 요일을 고르라는 검증이 없어서, 여기서 막으면 그 사용자는
        //    복습 세트를 저장할 수도 수정할 수도 없다. 예전 서버와 같게 매일로 저장한다.
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
