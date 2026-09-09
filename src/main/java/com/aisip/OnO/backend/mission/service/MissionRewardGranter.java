package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 보상 지급.
 *
 * <p><b>기존 {@code MissionLogService.addPointToUser} 를 타지 않는다.</b> 그쪽은 하루 200점 상한을 적용하는
 * 기존 적립 경로이고, 미션 보상은 그 위에 얹는 보너스라 상한 밖이다. 기존 적립 규칙은 손대지 않는다.
 *
 * <p>능력치 경험치 반영은 엔티티를 읽어 고치는 방식이라 같은 사용자의 동시 지급에 취약하다.
 * 기존 적립 경로와 같은 이유로 사용자 행을 배타 잠금으로 먼저 잡아 직렬화한다.
 * 잠금을 나중에 잡으면 잠금 승격 과정에서 교착이 난다.
 */
@Component
@RequiredArgsConstructor
public class MissionRewardGranter {

    private final UserRepository userRepository;

    /**
     * 지급 전에 사용자 행을 먼저 잠근다.
     *
     * <p>잠금 순서를 <b>사용자 → 진행도</b> 하나로 통일하기 위해서다. 기존 적립 경로
     * ({@code MissionLogService.registerLoginMission})는 사용자 행을 잠근 상태에서 진행도를 올리는데,
     * 받기가 진행도를 먼저 잠그고 사용자를 나중에 잠그면 두 요청이 서로의 잠금을 기다려 교착이 난다.
     */
    @Transactional
    public void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
    }

    @Transactional
    public GrantResult grant(Long userId, MissionMetric metric, MissionRewardType rewardType, int rewardValue) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));

        UserMissionStatus status = user.getUserMissionStatus();
        long levelBefore = status.getTotalStudyLevel();

        if (rewardType == MissionRewardType.XP && rewardValue > 0) {
            long point = rewardValue;
            switch (metric.getAbilityType()) {
                case ATTENDANCE -> status.gainAttendancePoint(point);
                case NOTE_WRITE -> status.gainNoteWritePoint(point);
                case PROBLEM_PRACTICE -> status.gainProblemPracticePoint(point);
                case NOTE_PRACTICE -> status.gainNotePracticePoint(point);
            }
        }

        long levelAfter = status.getTotalStudyLevel();
        return new GrantResult(levelAfter, levelAfter > levelBefore);
    }

    public record GrantResult(Long totalStudyLevel, boolean leveledUp) {
    }
}
