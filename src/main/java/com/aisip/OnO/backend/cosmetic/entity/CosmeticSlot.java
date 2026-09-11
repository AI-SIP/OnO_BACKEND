package com.aisip.OnO.backend.cosmetic.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 꾸미기 슬롯과 그리는 순서.
 *
 * <p>{@code layerOrder} 는 작을수록 뒤에 깔린다. 프론트가 이 순서를 코드에 박지 않도록
 * {@code GET /api/cosmetics} 응답에 목록으로 실어 보낸다. 나중에 슬롯이 늘거나 순서가 바뀌어도
 * 서버만 고치면 되고, 이미 깔린 앱도 새 순서를 따른다.
 *
 * <p>값 사이를 100 씩 띄운 것은 의도된 것이다. 나중에 두 슬롯 사이에 새 슬롯이 필요해지면
 * 기존 값을 건드리지 않고 그 사이 숫자를 쓰면 된다. {@link #BAG}(450) 이 실제로 그렇게 들어왔다.
 *
 * <p>{@link #BACK}(200) 과 {@link #BAG}(450) 은 둘 다 가방이지만 자리가 다르다.
 * 등에 메는 것은 본체(300)보다 뒤에 깔려야 하고, 앞으로 메는 것은 옷(400) 위에 올라가야 한다.
 * 한 슬롯으로 묶으면 둘 중 하나는 반드시 잘못 그려진다.
 *
 * <p>{@link #BASE} 는 장착 슬롯이 아니라 개구리 본체다. 본체 이미지도 {@code cosmetic_item} 행으로
 * 두기 때문에 슬롯 이름이 필요해서 여기 있을 뿐이고, 장착 가능한 슬롯 목록과 아이템 목록에서는 빠진다.
 * 그래서 {@code equippable} 로 갈라 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum CosmeticSlot {

    BACKGROUND(100, "배경", true),
    /** 등에 메는 가방. 개구리 본체보다 뒤에 깔린다. */
    BACK(200, "등짐", true),
    /** 개구리 본체. 장착 대상이 아니다. */
    BASE(300, "개구리 본체", false),
    OUTFIT(400, "옷", true),
    /** 앞으로 메는 가방. 옷 위에 올라간다. */
    BAG(450, "가방", true),
    NECK(500, "목", true),
    FACE(600, "얼굴", true),
    HEAD(700, "머리", true),
    HAND(800, "손", true),
    BADGE(850, "뱃지", true),
    EFFECT(900, "효과", true);

    private final int layerOrder;
    private final String nameKo;
    private final boolean equippable;

    /** 사용자가 실제로 걸 수 있는 슬롯만. 그리는 순서대로 준다. */
    public static List<CosmeticSlot> equippableSlots() {
        return Arrays.stream(values())
                .filter(CosmeticSlot::isEquippable)
                .sorted(java.util.Comparator.comparingInt(CosmeticSlot::getLayerOrder))
                .toList();
    }
}
