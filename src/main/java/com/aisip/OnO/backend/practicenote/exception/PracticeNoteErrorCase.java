package com.aisip.OnO.backend.practicenote.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PracticeNoteErrorCase implements ErrorCase {

    PRACTICE_NOTE_NOT_FOUND(404, 6001, "복습 노트를 찾을 수 없습니다."),

    PRACTICE_NOTE_USER_UNMATCHED(403, 6002, "복습 노트를 소유한 유저가 아닙니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
