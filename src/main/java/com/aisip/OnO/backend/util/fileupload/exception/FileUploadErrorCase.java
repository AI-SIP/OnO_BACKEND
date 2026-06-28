package com.aisip.OnO.backend.util.fileupload.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileUploadErrorCase implements ErrorCase {

    FILE_UPLOAD_FAILED(400, 2001, "파일 업로드 중 문제가 발생했습니다."),

    FILE_NOT_FOUND(404, 2002, "파일을 찾을 수 없습니다."),

    INVALID_IMAGE_FILE(400, 2003, "이미지 파일 형식이 올바르지 않습니다."),

    FILE_SIZE_EXCEEDED(400, 2004, "파일 최대 용량을 초과했습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
