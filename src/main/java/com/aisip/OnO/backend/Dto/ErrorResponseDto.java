package com.aisip.OnO.backend.Dto;

import lombok.*;

@Data
public class ErrorResponseDto {
    private String error;

    public ErrorResponseDto(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
