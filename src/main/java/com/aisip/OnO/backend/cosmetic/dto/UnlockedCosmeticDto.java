package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticItem;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

/**
 * 레벨업으로 이번에 열린 아이템. 보상 수령 응답에 실려 "새 아이템이 열렸어요" 화면을 띄우는 데 쓴다.
 *
 * <p>{@code owned} 를 담지 않는다. 여기 실렸다는 것 자체가 방금 열렸다는 뜻이다.
 */
public record UnlockedCosmeticDto(
        String itemKey,
        String nameKo,
        CosmeticSlot slot,
        String imageUrl
) {

    public static UnlockedCosmeticDto from(CosmeticItem item) {
        return new UnlockedCosmeticDto(
                item.getItemKey(),
                item.getNameKo(),
                item.getSlot(),
                item.getImageUrl()
        );
    }
}
