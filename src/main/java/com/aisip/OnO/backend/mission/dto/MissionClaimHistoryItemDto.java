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
 * <p>보상 종류와 값은 <b>현재</b> 미션 정의에서 읽는다. 진행도 행에 보상을 박아 두지 않기 때문에,
 * 운영 중에 보상을 바꾸면 예전에 받은 기록도 새 값으로 보인다.
 * 받은 시점의 값을 보존하려면 {@code target_snapshot} 처럼 보상 스냅샷 컬럼이 필요하다.
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
                definition.getRewardType(),
                definition.getRewardValue(),
                progress.getClaimedAt()
        );
    }
}
