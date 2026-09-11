package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

/**
 * 슬롯 하나와 그리는 순서.
 *
 * <p>순서를 응답에 실어 보내는 이유는 프론트가 레이어 순서를 코드에 박지 않게 하기 위해서다.
 * 슬롯이 늘거나 순서가 바뀌어도 이미 깔린 앱이 새 순서를 따른다.
 */
public record CosmeticSlotDto(
        CosmeticSlot slot,
        int layerOrder,
        String nameKo
) {

    public static CosmeticSlotDto from(CosmeticSlot slot) {
        return new CosmeticSlotDto(slot, slot.getLayerOrder(), slot.getNameKo());
    }
}
