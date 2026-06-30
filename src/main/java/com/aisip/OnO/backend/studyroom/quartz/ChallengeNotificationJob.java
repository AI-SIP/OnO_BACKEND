package com.aisip.OnO.backend.studyroom.quartz;

import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeNotificationJob implements Job {

    private final StudyRoomMemberRepository memberRepository;
    private final FcmService fcmService;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long roomId = Long.parseLong(dataMap.getString("roomId"));
        String challengeTitle = dataMap.getString("challengeTitle");
        String notificationType = dataMap.getString("notificationType");

        String title, body;
        if ("HALFWAY".equals(notificationType)) {
            title = "챌린지 중간 알림";
            body = "'" + challengeTitle + "' 챌린지가 절반 지났어요! 목표를 향해 달려가세요.";
        } else {
            title = "챌린지 마감 D-1";
            body = "'" + challengeTitle + "' 챌린지가 내일 마감돼요. 마무리 스퍼트!";
        }

        NotificationRequestDto dto = new NotificationRequestDto(null, title, body,
                Map.of("type", "CHALLENGE_NOTIFICATION", "roomId", String.valueOf(roomId)));

        memberRepository.findAllWithUserByRoomId(roomId).forEach(member -> {
            try {
                fcmService.sendNotificationToAllUserDevice(member.getUser().getId(), dto);
            } catch (Exception e) {
                log.warn("챌린지 알림 발송 실패 - userId: {}", member.getUser().getId(), e);
            }
        });
    }
}
