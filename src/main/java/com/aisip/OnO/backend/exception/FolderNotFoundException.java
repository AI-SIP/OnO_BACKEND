package com.aisip.OnO.backend.exception;

public class FolderNotFoundException extends RuntimeException{

    public FolderNotFoundException(String message) {
        super(message);
    }
}
