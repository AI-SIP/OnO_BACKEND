package com.aisip.OnO.backend.studyroom.quartz;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeNotificationScheduler {

    private static final String GROUP = "CHALLENGE_NOTIFICATION";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final Scheduler scheduler;
    private final ChallengeNotificationTimePolicy timePolicy;

    public void scheduleNotifications(StudyRoomChallenge challenge) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime startAt = challenge.getStartAt();
        LocalDateTime endAt = challenge.getEndAt();

        timePolicy.resolveHalfwayFireAt(startAt, endAt, now)
                .ifPresent(fireAt -> schedule(challenge, "HALFWAY", fireAt));

        timePolicy.resolveOneDayLeftFireAt(startAt, endAt, now)
                .ifPresent(fireAt -> schedule(challenge, "ONE_DAY_LEFT", fireAt));
    }

    public void cancelNotifications(Long challengeId) {
        cancelJob(challengeId, "HALFWAY");
        cancelJob(challengeId, "ONE_DAY_LEFT");
    }

    private void schedule(StudyRoomChallenge challenge, String type, LocalDateTime fireAt) {
        try {
            String key = "challenge-" + challenge.getId() + "-" + type;
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("roomId", String.valueOf(challenge.getRoom().getId()));
            dataMap.put("challengeTitle", challenge.getTitle());
            dataMap.put("notificationType", type);

            JobDetail jobDetail = JobBuilder.newJob(ChallengeNotificationJob.class)
                    .withIdentity(key, GROUP)
                    .usingJobData(dataMap)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(key, GROUP)
                    .startAt(Date.from(fireAt.atZone(ZONE).toInstant()))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("챌린지 알림 등록 - challengeId: {}, type: {}, fireAt: {}", challenge.getId(), type, fireAt);
        } catch (SchedulerException e) {
            log.warn("챌린지 알림 등록 실패 - challengeId: {}, type: {}", challenge.getId(), type, e);
        }
    }

    private void cancelJob(Long challengeId, String type) {
        try {
            String key = "challenge-" + challengeId + "-" + type;
            scheduler.unscheduleJob(TriggerKey.triggerKey(key, GROUP));
            scheduler.deleteJob(JobKey.jobKey(key, GROUP));
        } catch (SchedulerException e) {
            log.warn("챌린지 알림 취소 실패 - challengeId: {}, type: {}", challengeId, type, e);
        }
    }
}
