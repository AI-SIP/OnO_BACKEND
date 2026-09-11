package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;

import java.util.List;
import java.util.Map;

/**
 * 장착 결과.
 *
 * <p>바뀐 슬롯만이 아니라 <b>갱신된 장착 상태 전체</b>를 돌려준다. 프론트가 자기 쪽 상태를
 * 직접 계산해 맞추면, 충돌 자동 해제처럼 서버만 아는 규칙이 생길 때마다 양쪽이 어긋난다.
 * 서버가 만든 결과를 그대로 그리면 그 종류의 불일치가 없어진다.
 *
 * @param equipped         슬롯 이름 → 아이템 키. 비어 있는 슬롯은 키 자체가 없다.
 * @param unequippedSlots  이번 요청 때문에 자동으로 벗겨진 슬롯. 사용자가 직접 벗긴 것이 아니므로
 *                         프론트가 "가디건이 벗겨졌어요" 같은 안내를 띄울 수 있도록 따로 알려 준다.
 *                         충돌이 없으면 빈 배열이다.
 */
public record CosmeticEquipResponseDto(
        Map<CosmeticSlot, String> equipped,
        List<CosmeticSlot> unequippedSlots
) {
}
