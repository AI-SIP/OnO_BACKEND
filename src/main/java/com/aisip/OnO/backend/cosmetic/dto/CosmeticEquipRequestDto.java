package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import jakarta.validation.constraints.NotNull;

/**
 * 슬롯 하나 장착/해제 요청.
 *
 * <p>{@code itemKey} 가 null 이면 그 슬롯을 해제한다. 그래서 이 필드에는 {@code @NotNull} 이 없다.
 *
 * <p>{@code slot} 은 아이템에서 유도할 수도 있지만 굳이 함께 받는다. 프론트가 "머리 슬롯을 바꾼다"고
 * 보낸 요청이 서버에서 엉뚱한 슬롯을 건드리면 사용자 눈에는 원인 없는 버그로 보인다.
 * 둘이 어긋나면 조용히 아이템 쪽을 따르지 않고 400 으로 거절한다. 해제 요청은 아이템이 없으므로
 * 이 값이 유일한 대상 지정 수단이기도 하다.
 */
public record CosmeticEquipRequestDto(
        @NotNull(message = "슬롯을 지정해야 합니다.")
        CosmeticSlot slot,
        String itemKey
) {
}
