package com.aisip.OnO.backend.practicenote.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PracticeNoteErrorCase implements ErrorCase {

    PRACTICE_NOTE_NOT_FOUND(400, 1001, "복습 노트를 찾을 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
