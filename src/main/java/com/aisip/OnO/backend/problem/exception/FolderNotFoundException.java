package com.aisip.OnO.backend.problem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // HTTP 404 반환
public class FolderNotFoundException extends RuntimeException{

    private static final String DEFAULT_MESSAGE = "폴더를 찾을 수 없습니다.";

    public FolderNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public FolderNotFoundException(Long folderId) {
        super("Folder ID: " + folderId + "인 " + DEFAULT_MESSAGE);
    }

    public FolderNotFoundException(String message) {
        super(message);
    }
}
