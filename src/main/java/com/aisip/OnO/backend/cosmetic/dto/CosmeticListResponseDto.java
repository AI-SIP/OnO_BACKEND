package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/cosmetics} 응답. 프론트와 맞춘 형태라 필드 이름이 바뀌면 앱이 그대로 깨진다.
 *
 * @param baseImageUrl  개구리 본체 이미지. 슬롯 아이템이 아니라 항상 그려진다.
 * @param baseLayerOrder 본체를 어느 층에 그릴지. 본체는 가방(200)과 옷(400) 사이에 들어간다.
 *                       슬롯 목록에는 본체가 없으므로 이 값이 따로 있어야 프론트가 레이어 순서를
 *                       코드에 박지 않고 조립할 수 있다.
 * @param slots         장착 가능한 슬롯과 그리는 순서. 뒤에서 앞 순서로 정렬돼 온다.
 * @param items         활성 아이템 전부. 잠긴 것도 {@code owned: false} 로 함께 내려간다.
 *                      화면에 "레벨 6 에 열림" 을 보여주려면 잠긴 것도 알아야 한다.
 * @param equipped      슬롯 이름 → 아이템 키. 비어 있는 슬롯은 키 자체가 없다.
 */
public record CosmeticListResponseDto(
        String baseImageUrl,
        int baseLayerOrder,
        List<CosmeticSlotDto> slots,
        List<CosmeticItemResponseDto> items,
        Map<CosmeticSlot, String> equipped
) {
}
