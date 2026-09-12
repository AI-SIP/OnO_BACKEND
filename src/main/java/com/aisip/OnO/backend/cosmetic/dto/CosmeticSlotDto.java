package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

/**
 * 슬롯 하나와 그리는 순서.
 *
 * <p>순서를 응답에 실어 보내는 이유는 프론트가 레이어 순서를 코드에 박지 않게 하기 위해서다.
 * 슬롯이 늘거나 순서가 바뀌어도 이미 깔린 앱이 새 순서를 따른다.
 *
 * <p>{@code composited} 는 그 자리를 개구리 그림에 겹쳐 그리는지다. {@code FRAME} 만 false 다.
 * 원형 프로필 사진의 테두리라 옷장 목록에는 나가지만 개구리 합성에서는 빠진다.
 * 이 값 없이 {@code layerOrder} 만 내려보내면 프론트는 순서대로 겹치는 수밖에 없어
 * 프레임이 개구리 위에 덮인다. 뒤에 덧붙인 필드라 구버전 앱은 그대로 돈다.
 */
public record CosmeticSlotDto(
        CosmeticSlot slot,
        int layerOrder,
        String nameKo,
        boolean composited
) {

    public static CosmeticSlotDto from(CosmeticSlot slot) {
        return new CosmeticSlotDto(slot, slot.getLayerOrder(), slot.getNameKo(), slot.isComposited());
    }
}
