package com.aisip.OnO.backend.mission.dto;

import com.aisip.OnO.backend.mission.entity.MissionCategory;
import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;

/**
 * 미션 한 건.
 *
 * <p>{@code progressId} 는 아직 한 번도 손대지 않은 미션에서 null 이다. 받기는 이 값으로 하는데,
 * 완료된 미션에는 반드시 진행도 행이 있으므로 완료 상태에서 null 이 되는 경우는 없다.
 *
 * <p>{@code periodKey} 를 항목마다 싣는다. expired 묶음에는 서로 다른 기간의 미션이 함께 들어오므로
 * 묶음 하나에 기간 키 하나를 다는 것으로는 "언제 완료한 것인지"를 표현할 수 없다.
 */
public record MissionResponseDto(
        Long progressId,
        String periodKey,
        String code,
        String title,
        String description,
        String iconKey,
        MissionCategory category,
        int current,
        int target,
        boolean completed,
        boolean claimed,
        MissionRewardType rewardType,
        int rewardValue
) {

    public static MissionResponseDto from(MissionDefinition definition, MissionProgress progress, String periodKey) {
        return new MissionResponseDto(
                progress == null ? null : progress.getId(),
                periodKey,
                definition.getCode(),
                definition.getTitle(),
                definition.getDescription(),
                definition.getIconKey(),
                definition.getCategory(),
                progress == null ? 0 : progress.getCurrentValue(),
                // 진행도가 있으면 그 행이 만들어질 때 박아둔 목표로 보여준다.
                // 운영 중에 목표를 낮추면 이미 채운 사람의 화면이 "5 / 3" 이 되기 때문이다.
                progress == null ? definition.getTarget() : progress.getTargetSnapshot(),
                progress != null && progress.isCompleted(),
                progress != null && progress.isClaimed(),
                definition.getRewardType(),
                definition.getRewardValue()
        );
    }
}
