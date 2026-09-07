package com.aisip.OnO.backend.notice.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoticeErrorCase implements ErrorCase {

    NOTICE_NOT_FOUND(404, 14001, "공지를 찾을 수 없습니다."),

    NOTICE_TITLE_INVALID(400, 14002, "공지 제목은 1자 이상 100자 이하여야 합니다."),

    NOTICE_CONTENT_INVALID(400, 14003, "공지 내용은 1자 이상 500자 이하여야 합니다."),

    NOTICE_DURATION_INVALID(400, 14004, "공지 노출 시간은 1시간 이상 168시간 이하여야 합니다."),

    NOTICE_TYPE_REQUIRED(400, 14005, "공지 유형을 지정해야 합니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
