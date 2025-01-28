package com.aisip.OnO.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // HTTP 404 반환
public class FolderNotFoundException extends RuntimeException{

    public FolderNotFoundException(String message) {
        super(message);
    }
}
