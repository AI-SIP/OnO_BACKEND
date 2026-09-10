package com.aisip.OnO.backend.mission.dto;

import com.aisip.OnO.backend.mission.entity.MissionRewardType;

/**
 * 보상 수령 결과. 지급 직후의 총 학습 레벨과 이번 지급으로 레벨이 올랐는지를 담는다.
 */
public record MissionClaimResponseDto(
        Long progressId,
        MissionRewardType rewardType,
        int rewardValue,
        Long totalStudyLevel,
        boolean leveledUp
) {
}
