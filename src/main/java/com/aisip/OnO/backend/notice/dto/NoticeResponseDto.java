package com.aisip.OnO.backend.notice.dto;

import com.aisip.OnO.backend.notice.entity.NoticeType;
import com.aisip.OnO.backend.notice.entity.ServiceNotice;

import java.time.LocalDateTime;

public record NoticeResponseDto(
        Long noticeId,
        String title,
        String content,
        NoticeType type,
        LocalDateTime expiresAt
) {
    public static NoticeResponseDto from(ServiceNotice notice) {
        return new NoticeResponseDto(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getType(),
                notice.getExpiresAt()
        );
    }
}
