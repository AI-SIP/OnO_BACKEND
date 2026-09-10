package com.aisip.OnO.backend.mission.dto;

import com.aisip.OnO.backend.mission.entity.MissionCategory;
import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;

import java.time.LocalDateTime;

/**
 * 보상을 받은 기록 한 건.
 *
 * <p>새 테이블을 두지 않는다. {@code mission_progress.claimed_at} 이 이미 그 기록이다.
 *
 * <p>보상 종류와 값은 <b>받은 시점의 스냅샷</b>에서 읽는다. 현재 정의를 읽으면 운영 중에 보상을 바꿨을 때
 * 예전에 받은 기록까지 새 값으로 보인다. 스냅샷이 없는 옛 행만 현재 정의로 폴백한다.
 */
public record MissionClaimHistoryItemDto(
        Long progressId,
        String code,
        String title,
        String iconKey,
        MissionCategory category,
        String periodKey,
        MissionRewardType rewardType,
        int rewardValue,
        LocalDateTime claimedAt
) {

    public static MissionClaimHistoryItemDto from(MissionDefinition definition, MissionProgress progress) {
        return new MissionClaimHistoryItemDto(
                progress.getId(),
                definition.getCode(),
                definition.getTitle(),
                definition.getIconKey(),
                definition.getCategory(),
                progress.getPeriodKey(),
                progress.rewardTypeOr(definition.getRewardType()),
                progress.rewardValueOr(definition.getRewardValue()),
                progress.getClaimedAt()
        );
    }
}
