package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.fcm.service.FcmService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.Map;

@Slf4j
public class PracticeNotificationJob extends QuartzJobBean {

    @Autowired
    private FcmService fcmService;

    @Override
    public void executeInternal(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long userId = dataMap.getLong("userId");
        Long practiceId = dataMap.getLong("practiceId");
        String practiceTitle = dataMap.getString("practiceTitle");

        log.info("[Quartz 실행] userId: {}, title: {}", userId, practiceTitle);

        fcmService.sendNotificationToAllUserDevice(
                userId,
                new NotificationRequestDto(
                        "User Token",
                        "복습할 시간이예요!",
                        practiceTitle + " 복습노트를 공부할 시간입니다!",
                        Map.of("practiceId", String.valueOf(practiceId))
                )
        );
    }
}