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
 *
 * <p>{@code composited} 는 <b>{@code equippable} 과 다른 축이다.</b> 걸 수는 있지만 개구리 그림에는
 * 겹치지 않는 자리가 있다({@link #FRAME}). {@code layerOrder} 만 내려보내면 프론트는 순서대로 겹쳐
 * 그리는 수밖에 없어서, 프레임이 개구리 위에 덮인다. 어느 자리가 합성 대상인지도 데이터로 내려보낸다.
 * 이 값이 없으면 프론트가 "FRAME 은 예외" 를 코드에 박아야 하는데, 그건 {@code layerOrder} 를
 * 응답에 실어 보내기로 한 이유를 그대로 무르는 것이다.
 */
@Getter
@RequiredArgsConstructor
public enum CosmeticSlot {

    BACKGROUND(100, "배경", true, true),
    /** 등에 메는 가방. 개구리 본체보다 뒤에 깔린다. */
    BACK(200, "등짐", true, true),
    /** 개구리 본체. 장착 대상이 아니다. */
    BASE(300, "개구리 본체", false, true),
    OUTFIT(400, "옷", true, true),
    /** 앞으로 메는 가방. 옷 위에 올라간다. */
    BAG(450, "가방", true, true),
    NECK(500, "목", true, true),
    FACE(600, "얼굴", true, true),
    HEAD(700, "머리", true, true),
    HAND(800, "손", true, true),
    BADGE(850, "뱃지", true, true),
    EFFECT(900, "효과", true, true),
    /**
     * 원형 프로필 사진의 테두리. <b>개구리에 겹치지 않는다.</b>
     *
     * <p>걸 수 있는 자리라 옷장 목록에는 나가지만 개구리 합성에서는 빠지고 프로필 위젯이 따로 쓴다.
     * {@code layerOrder} 1000 은 옷장에서의 자리 순서를 정하려고 둔 값이지 개구리 위에 그린다는 뜻이 아니다.
     * 그 구분은 {@code composited = false} 가 한다.
     *
     * <p>에셋도 혼자 다르다. 나머지는 {@code assets/Cosmetic/{item_key}.png} 인데
     * 프레임만 {@code assets/ProfileFrame/{item_key}.svg} 다.
     */
    FRAME(1000, "프레임", true, false);

    private final int layerOrder;
    private final String nameKo;
    private final boolean equippable;
    /** 개구리 그림에 겹쳐 그리는 자리인지. {@link #FRAME} 만 false 다. */
    private final boolean composited;

    /** 사용자가 실제로 걸 수 있는 슬롯만. 그리는 순서대로 준다. */
    public static List<CosmeticSlot> equippableSlots() {
        return Arrays.stream(values())
                .filter(CosmeticSlot::isEquippable)
                .sorted(java.util.Comparator.comparingInt(CosmeticSlot::getLayerOrder))
                .toList();
    }
}
