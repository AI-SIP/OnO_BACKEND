package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

import java.util.List;

/**
 * 아이템 한 건.
 *
 * <p>{@code owned} 는 저장된 값이 아니라 조회 시점에 계산한 값이다
 * ({@code requiredLevel != null && requiredLevel <= 총 학습 레벨}).
 * 그런데도 목록으로 실어 보내는 이유는, 나중에 시즌 보상이나 이벤트로 획득 경로가 늘어도
 * 프론트는 이 불리언만 보면 되게 하기 위해서다. 해금 규칙이 바뀌어도 앱은 그대로다.
 */
public record CosmeticItemResponseDto(
        String itemKey,
        CosmeticSlot slot,
        String nameKo,
        String imageUrl,
        Integer requiredLevel,
        String setId,
        List<String> conflictsWith,
        boolean owned
) {

    public static CosmeticItemResponseDto of(CosmeticItem item, long totalStudyLevel) {
        return new CosmeticItemResponseDto(
                item.getItemKey(),
                item.getSlot(),
                item.getNameKo(),
                item.getImageUrl(),
                item.getRequiredLevel(),
                item.getSetId(),
                item.conflictKeys(),
                item.isOwnedAt(totalStudyLevel)
        );
    }
}
