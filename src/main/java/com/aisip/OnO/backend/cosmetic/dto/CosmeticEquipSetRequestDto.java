package com.aisip.OnO.backend.cosmetic.dto;

import jakarta.validation.constraints.NotBlank;

/** 세트 장착 요청. 세트에 속한 아이템을 각자의 슬롯에 한 번에 건다. */
public record CosmeticEquipSetRequestDto(
        @NotBlank(message = "세트를 지정해야 합니다.")
        String setId
) {
}
