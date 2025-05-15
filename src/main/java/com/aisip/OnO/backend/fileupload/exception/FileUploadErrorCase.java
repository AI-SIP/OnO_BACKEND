package com.aisip.OnO.backend.fileupload.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileUploadErrorCase implements ErrorCase {

    FILE_UPLOAD_FAILED(400, 2001, "파일 업로드 중 문제가 발생했습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
