package com.aisip.OnO.backend.mission.dto;

import com.aisip.OnO.backend.cosmetic.dto.UnlockedCosmeticDto;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;

import java.util.List;

/**
 * 보상 수령 결과. 지급 직후의 총 학습 레벨과 이번 지급으로 레벨이 올랐는지를 담는다.
 *
 * <p>{@code unlockedCosmetics} 는 이번 지급으로 레벨이 올라 새로 열린 꾸미기 아이템이다.
 * 기존 필드는 그대로 두고 뒤에 덧붙였다. 구버전 앱은 모르는 필드를 무시하므로 그대로 돈다.
 * 레벨이 안 올랐으면 빈 배열이고, 여러 단계 올랐으면 그 사이에 열린 것이 전부 들어온다.
 * null 이 아니라 빈 배열로 내려보낸다. 프론트가 null 검사를 따로 하지 않아도 되게.
 */
public record MissionClaimResponseDto(
        Long progressId,
        MissionRewardType rewardType,
        int rewardValue,
        Long totalStudyLevel,
        boolean leveledUp,
        List<UnlockedCosmeticDto> unlockedCosmetics
) {
}
