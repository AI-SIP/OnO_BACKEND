package com.aisip.OnO.backend.folder.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FolderErrorCase implements ErrorCase {

    FOLDER_NOT_FOUND(400, 5001, "폴더를 찾을 수 없습니다."),

    FOLDER_USER_UNMATCHED(400, 5002, "폴더를 소유한 유저가 아닙니다."),

    ROOT_FOLDER_NOT_EXIST(400, 5003, "루트 폴더가 존재하지 않습니다."),

    ROOT_FOLDER_CANNOT_REMOVE(400, 5004, "루트 폴더는 삭제할 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
