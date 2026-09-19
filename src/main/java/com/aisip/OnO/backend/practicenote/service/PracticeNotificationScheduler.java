
package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import com.aisip.OnO.backend.practicenote.exception.PracticeNoteErrorCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PracticeNotificationScheduler {

    private final Scheduler scheduler;

    public void schedulePracticeNotification(Long userId, Long practiceId, String practiceTitle, PracticeNotificationRegisterDto dto) {
        validateNotification(dto);

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
        // 잡을 지운 뒤에 검증에 걸리면 기존 알림만 사라진다. Quartz 잡 삭제는 서비스 트랜잭션과
        // 함께 롤백되지 않으므로, 지우기 전에 먼저 막는다.
        validateNotification(dto);

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

    /**
     * 주간 반복인데 요일이 비어 있으면 거절한다.
     *
     * <p>예전에는 이 요청이 아래 크론 변환의 매일 폴백으로 흘러가, 사용자가 고르지도 않은
     * 매일 알림이 등록됐다. 잘못된 요청이라는 신호 없이 동작만 달라지는 쪽이 더 나쁘다.
     */
    private void validateNotification(PracticeNotificationRegisterDto dto) {
        if (dto.isWeeklyWithoutWeekDays()) {
            throw new ApplicationException(PracticeNoteErrorCase.PRACTICE_NOTIFICATION_WEEK_DAYS_REQUIRED);
        }
    }

    private String convertDtoToCron(PracticeNotificationRegisterDto dto) {
        int hour = dto.hour();
        int minute = dto.minute();

        if ("daily".equalsIgnoreCase(dto.repeatType())) {
            // 매일 지정된 시각에 실행
            return String.format("0 %d %d ? * *", minute, hour);
        } else if ("weekly".equalsIgnoreCase(dto.repeatType())) {
            // 선택한 요일에만 지정된 시각에 실행 (e.g. MON,WED,FRI)
            // 요일이 비어 있는 경우는 validateNotification 이 이미 걸러 냈다.
            String dayString = dto.weekDays().stream()
                    .map(this::convertDayToQuartz)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("*");

            return String.format("0 %d %d ? * %s", minute, hour, dayString);
        }

        // daily/weekly 가 아닌 값(null 포함)은 지금처럼 매일로 둔다.
        // 구버전 앱이 repeatType 을 비워 보내는 경우까지 여기서 막으면 기존 알림이 통째로 끊긴다.
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
