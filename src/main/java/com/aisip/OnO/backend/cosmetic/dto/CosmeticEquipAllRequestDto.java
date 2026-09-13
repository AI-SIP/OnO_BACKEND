package com.aisip.OnO.backend.cosmetic.dto;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 차림 전체 저장 요청. 꾸미기 화면의 저장 버튼 한 번이 이 본문 하나로 나간다.
 *
 * <p>{@code equipped} 는 <b>바뀐 슬롯이 아니라 저장할 차림 전부</b>다. 여기 없는 슬롯은 비운다.
 * "안 보낸 것은 그대로 둔다" 로 하면 시착 화면에서 벗어 놓고 저장한 것을 표현할 방법이 없어진다.
 *
 * <p>그래서 <b>빈 맵은 유효한 요청</b>이다. 전부 벗는다는 뜻이라 {@code @NotEmpty} 가 아니라
 * {@code @NotNull} 이다. 필드 자체가 빠진 본문(맵이 null)은 "전부 벗기" 인지 "보내다 만 것" 인지
 * 구별할 수 없어 거절한다.
 *
 * <p>값이 {@code null} 인 항목은 그 슬롯을 비우라는 뜻으로, 키를 아예 빼고 보낸 것과 같게 다룬다.
 * 프론트가 빈 자리를 {@code null} 로 채워 보내든 빼고 보내든 같은 결과가 되어야 한다.
 *
 * <p>사용자 식별자는 여기에 담지 않는다. 본문으로 받으면 남의 차림을 바꾸는 요청을 그대로 받아들이게
 * 된다. 다른 꾸미기 API 와 같이 인증 컨텍스트에서만 꺼낸다.
 */
public record CosmeticEquipAllRequestDto(
        @NotNull(message = "장착 상태를 지정해야 합니다.")
        Map<CosmeticSlot, String> equipped
) {
}
