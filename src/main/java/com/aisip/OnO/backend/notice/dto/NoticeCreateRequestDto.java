package com.aisip.OnO.backend.notice.dto;

import com.aisip.OnO.backend.notice.entity.NoticeType;

/**
 * 관리자 공지 등록 요청.
 *
 * @param durationHours 노출 시간. 비우면 기본 24시간으로 채운다.
 */
public record NoticeCreateRequestDto(
        String title,
        String content,
        NoticeType type,
        Integer durationHours
) {
}
