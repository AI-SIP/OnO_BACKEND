package com.aisip.OnO.backend.common.emoji;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomEmojiErrorCase implements ErrorCase {

    INVALID_EMOJI_KEY(400, 11001, "지원하지 않는 이모지입니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
