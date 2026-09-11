package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticUnlockLevels;
import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;

import java.util.List;

/**
 * 아이템 한 건.
 *
 * <p>{@code owned} 는 저장된 값이 아니라 조회 시점에 계산한 값이다. {@code requiredAbility} 가
 * 있으면 그 능력치 레벨과, 없으면 총 학습 레벨과 {@code requiredLevel} 을 비교한다.
 * 그런데도 목록으로 실어 보내는 이유는, 나중에 시즌 보상이나 이벤트로 획득 경로가 늘어도
 * 프론트는 이 불리언만 보면 되게 하기 위해서다. 해금 규칙이 바뀌어도 앱은 그대로다.
 *
 * <p>{@code requiredAbility} 를 함께 내려보내는 이유는 화면 문구 때문이다. 이 값이 없으면
 * 잠긴 아이템에 "Lv.5 에 열려요" 까지만 쓸 수 있고 어느 레벨을 올려야 하는지는 쓸 수 없다.
 *
 * <p>필드는 뒤에 덧붙이기만 했다. 구버전 앱은 모르는 필드를 무시하므로 그대로 돈다.
 */
public record CosmeticItemResponseDto(
        String itemKey,
        CosmeticSlot slot,
        String nameKo,
        String imageUrl,
        Integer requiredLevel,
        AbilityType requiredAbility,
        boolean fullBody,
        String setId,
        String setNameKo,
        List<String> conflictsWith,
        boolean owned
) {

    public static CosmeticItemResponseDto of(CosmeticItem item, CosmeticUnlockLevels levels) {
        return new CosmeticItemResponseDto(
                item.getItemKey(),
                item.getSlot(),
                item.getNameKo(),
                item.getImageUrl(),
                item.getRequiredLevel(),
                item.getRequiredAbility(),
                item.isFullBody(),
                item.getSetId(),
                item.getSetNameKo(),
                item.conflictKeys(),
                item.isOwnedBy(levels)
        );
    }
}
