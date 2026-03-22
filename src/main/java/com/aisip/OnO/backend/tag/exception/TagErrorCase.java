package com.aisip.OnO.backend.tag.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TagErrorCase implements ErrorCase {

    TAG_NAME_EMPTY(400, 9001, "태그명은 비어 있을 수 없습니다."),

    TAG_NAME_TOO_LONG(400, 9002, "태그명은 30자 이하여야 합니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
