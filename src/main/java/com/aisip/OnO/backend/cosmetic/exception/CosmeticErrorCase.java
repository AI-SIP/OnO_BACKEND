package com.aisip.OnO.backend.cosmetic.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 꾸미기 도메인 에러. 15000 대를 쓴다. 앞의 14000 대까지는 이미 다른 도메인이 쓰고 있다.
 */
@Getter
@RequiredArgsConstructor
public enum CosmeticErrorCase implements ErrorCase {

    USER_NOT_FOUND(404, 15001, "해당하는 유저가 존재하지 않습니다."),

    /** 없는 키이거나 비활성 아이템. 어느 쪽인지 구분해 주지 않는다. 2차 콘텐츠 목록이 새어 나간다. */
    COSMETIC_ITEM_NOT_FOUND(404, 15002, "존재하지 않는 꾸미기 아이템입니다."),

    COSMETIC_ITEM_NOT_OWNED(400, 15003, "아직 잠겨 있는 꾸미기 아이템입니다."),

    /** 요청한 슬롯과 아이템이 실제로 속한 슬롯이 다른 경우. */
    COSMETIC_SLOT_MISMATCH(400, 15004, "아이템을 걸 수 없는 슬롯입니다."),

    COSMETIC_SET_NOT_FOUND(404, 15005, "존재하지 않는 꾸미기 세트입니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
